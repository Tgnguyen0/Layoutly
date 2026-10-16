import {createServer} from 'node:http'
import {readFile,stat} from 'node:fs/promises'
import path from 'node:path'
const root=path.resolve(process.env.LAYOUTLY_FIDELITY_OUTPUT||'../.local-build/fidelity/before')
const mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.png':'image/png','.json':'application/json','.woff2':'font/woff2'}
createServer(async(req,res)=>{
  try {
    const requested=decodeURIComponent(new URL(req.url,'http://localhost').pathname)
    if(requested==='/health'){res.end('ok');return}
    const fixture=req.headers['x-layoutly-fixture'], format=req.headers['x-layoutly-format']
    if(!/^[a-z0-9-]+$/.test(fixture||'')||!['html','react'].includes(format)){res.writeHead(400).end();return}
    const mounted=path.join(root,fixture,format==='react'?'react/dist':'html')
    let file=path.resolve(mounted,'.'+requested)
    if(!file.startsWith(mounted+path.sep)&&file!==mounted){res.writeHead(403).end();return}
    try{if((await stat(file)).isDirectory())file=path.join(file,'index.html')}catch{
      // React BrowserRouter history fallback, confined to that fixture's dist.
      if(format==='react' && !path.extname(requested))file=path.join(mounted,'index.html');else throw new Error('missing')
    }
    res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'})
    res.end(await readFile(file))
  } catch {res.writeHead(404).end('Missing fixture resource')}
}).listen(59991,'127.0.0.1',()=>console.log('Fidelity static server 59991'))
