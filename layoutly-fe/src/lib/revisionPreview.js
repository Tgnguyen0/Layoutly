import { snapshotApi, aiFilePath } from './api.js'
import { assetDataUrl } from './assetData.js'
const runtimeURL = '/preview-runtime.js'

function dataModule(source) { return 'data:text/javascript;charset=utf-8,' + encodeURIComponent(source) }
export function resolveProjectPath(from, specifier) {
  const parts = (specifier.startsWith('/') ? specifier.slice(1) : from.split('/').slice(0, -1).join('/') + '/' + specifier).split('/')
  const result = []
  for (const part of parts) { if (part === '..') result.pop(); else if (part && part !== '.') result.push(part) }
  return result.join('/')
}
function findFile(files, path) { return [path, path + '.jsx', path + '.js', path + '.tsx', path + '.ts', path + '/index.jsx', path + '/index.js'].find(item => files.includes(item)) }
function runtimeModule(name, exports) { return dataModule(`const runtime = globalThis.__layoutlyRuntime.${name}; export default runtime; ${exports.map(key => `export const ${key}=runtime.${key};`).join('')}`) }

// Compile source without running it in the editor origin. The result only executes
// inside PreviewCanvas's allow-scripts iframe (without allow-same-origin).
export async function revisionPreview(snapshotId, revision, signal) {
  const framework = revision.targetFramework || 'REACT'
  const revisionId = revision.revisionId
  const files = (await snapshotApi.aiFiles(snapshotId, revisionId, framework)).json || []
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
  const privateAssets = new Map()
  await Promise.all(files.filter(path => /\.(png|jpe?g|svg|gif|webp|avif|woff2?|ttf|otf|ico)$/i.test(path)).map(async path => {
    privateAssets.set(path, await assetDataUrl('/api' + aiFilePath(snapshotId, path, revisionId, framework), signal))
  }))
  const asset = path => privateAssets.get(path) || new URL('/api' + aiFilePath(snapshotId, path, revisionId, framework), location.origin).href
  const texts = new Map()
  async function read(path) { if (!texts.has(path)) texts.set(path, (await snapshotApi.aiFile(snapshotId, path, revisionId, framework)).raw); return texts.get(path) }
  function cssAssets(css, file) { return css.replace(/url\(\s*(['"]?)([^)'"\s]+)\1\s*\)/g, (all, quote, value) => /^(data:|https?:|#|\/\/)/.test(value) ? all : `url("${asset(resolveProjectPath(file, value))}")`) }
  if (framework === 'HTML') {
    const entry = files.find(path => path === 'index.html') || files.find(path => path.endsWith('.html'))
    if (!entry) throw new Error('Revision không có file HTML để preview.')
    const doc = new DOMParser().parseFromString(await read(entry), 'text/html')
    for (const link of [...doc.querySelectorAll('link[rel="stylesheet"]')]) { const path = resolveProjectPath(entry, link.getAttribute('href')); if (files.includes(path)) { const style = doc.createElement('style'); style.textContent = cssAssets(await read(path), path); link.replaceWith(style) } }
    for (const image of doc.querySelectorAll('[src]')) { const value = image.getAttribute('src'); if (/^(data:|https?:|\/\/)/.test(value)) continue; const path = resolveProjectPath(entry, value); if (image.tagName === 'SCRIPT') { if (files.includes(path)) { image.removeAttribute('src'); image.textContent = await read(path) } } else image.setAttribute('src', asset(path)) }
    return '<!doctype html>' + doc.documentElement.outerHTML
  }
  const { transform } = await import('sucrase')
  const imports = {
    react: runtimeModule('React', ['useState', 'useEffect', 'useLayoutEffect', 'useMemo', 'useCallback', 'useRef', 'useContext', 'useReducer', 'useId', 'createContext', 'createElement', 'Fragment', 'memo', 'forwardRef', 'StrictMode', 'lazy', 'Suspense']),
    'react-dom/client': runtimeModule('ReactDOM', ['createRoot', 'hydrateRoot']),
    'react-dom': runtimeModule('ReactDOMLegacy', ['flushSync', 'createPortal']),
    'react-router-dom': runtimeModule('Router', ['BrowserRouter', 'HashRouter', 'MemoryRouter', 'Routes', 'Route', 'Navigate', 'Link', 'NavLink', 'Outlet', 'useLocation', 'useNavigate', 'useParams', 'useSearchParams']),
  }
  const sourceFiles = files.filter(path => /\.(jsx?|tsx?|css|json)$/.test(path) && !/^(vite\.config|package\.json)/.test(path))
  await Promise.all(sourceFiles.map(async path => {
    let source = await read(path)
    if (path.endsWith('.css')) source = `const style=document.createElement('style');style.textContent=${JSON.stringify(cssAssets(source, path))};document.head.append(style);export default {};`
    else if (path.endsWith('.json')) source = `export default ${source};`
    else {
      source = transform(source, { transforms: ['jsx', ...(path.endsWith('.ts') || path.endsWith('.tsx') ? ['typescript'] : [])], production: true, filePath: path }).code
      source = source.replace(/((?:\bfrom\s*|\bimport\s*|\bimport\s*\(\s*)['"])([^'"\n]+)(['"])/g, (all, before, specifier, after) => {
        if (!specifier.startsWith('.') && !specifier.startsWith('/')) { if (!imports[specifier]) throw new Error(`Preview chưa hỗ trợ dependency: ${specifier}`); return all }
        const requested = resolveProjectPath(path, specifier)
        const resolved = findFile(files, requested)
        if (!resolved) throw new Error(`Không tìm thấy file: ${requested}`)
        if (!sourceFiles.includes(resolved)) { const key = 'layoutly:/' + resolved; imports[key] = dataModule(`export default ${JSON.stringify(asset(resolved))}`); return before + key + after }
        return before + 'layoutly:/' + resolved + after
      })
    }
    imports['layoutly:/' + path] = dataModule(source)
  }))
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
  const entry = files.find(path => /^src\/main\.[jt]sx?$/.test(path))
  if (!entry) throw new Error('Revision không có src/main.jsx để khởi động preview.')
  const escape = value => value.replace(/</g, '\\u003c')
  return `<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><script src="${new URL(runtimeURL, location.origin).href}"></script><script type="importmap">${escape(JSON.stringify({ imports }))}</script></head><body style="margin:0"><div id="root"></div><script type="module">import 'layoutly:/${entry}';</script></body></html>`
}
