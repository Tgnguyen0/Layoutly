type ExportScope = 'SELECTION' | 'PAGE'
type RootNode = SceneNode | PageNode

interface BridgeAsset {
  nodeId: string
  fileName: string
  contentType: 'image/png' | 'image/svg+xml'
  bytes: Uint8Array
}

interface SyncRequest {
  type: 'sync'
  scope: ExportScope
  backendUrl: string
}

interface OpenRequest {
  type: 'open-layoutly'
  url: string
}

figma.showUI(__html__, { width: 420, height: 610, themeColors: true })

function sendSelection(): void {
  const selection = figma.currentPage.selection[0]
  figma.ui.postMessage({
    type: 'selection',
    selection: selection
      ? {
          name: selection.name,
          id: selection.id,
          nodeType: selection.type,
          approximateNodeCount: countNodes(selection, 10000),
        }
      : null,
    page: {
      name: figma.currentPage.name,
      id: figma.currentPage.id,
      approximateNodeCount: countNodes(figma.currentPage, 20000),
    },
  })
}

figma.on('selectionchange', sendSelection)
sendSelection()

figma.ui.onmessage = async (message: SyncRequest | OpenRequest) => {
  if (message.type === 'open-layoutly') {
    figma.openExternal(message.url)
    return
  }
  if (message.type !== 'sync') return

  try {
    const root = resolveRoot(message.scope)
    progress('Reading design', 1)
    const approximateNodeCount = countNodes(root, 50000)
    const warnings: string[] = []

    progress('Preparing design data', 2)
    const document = await serializeDesignRoot(root, warnings)
    const exportedAt = new Date().toISOString()
    const designJson = JSON.stringify({
      name: figma.root.name || 'Figma Document',
      version: `bridge-${Date.now()}`,
      lastModified: exportedAt,
      document,
    })

    progress('Exporting assets', 3)
    const assets = await exportAssets(root, warnings)
    const referenceImage = message.scope === 'SELECTION'
      ? await exportReference(root, warnings)
      : null

    const metadata = {
      source: 'FIGMA_PLUGIN',
      fileKey: figma.fileKey || null,
      fileName: figma.root.name || 'Figma Document',
      pageName: figma.currentPage.name,
      nodeId: root.id,
      nodeName: root.name,
      nodeType: root.type === 'PAGE' ? 'CANVAS' : root.type,
      exportedAt,
      approximateNodeCount,
      assets: assets.map(({ nodeId, fileName, contentType }) => ({ nodeId, fileName, contentType })),
      warnings,
    }

    progress('Uploading to Layoutly', 4)
    figma.ui.postMessage({
      type: 'upload',
      backendUrl: normalizeBackendUrl(message.backendUrl),
      metadata,
      designJson,
      assets,
      referenceImage,
    })
  } catch (error) {
    figma.ui.postMessage({ type: 'error', message: errorMessage(error) })
  }
}

function resolveRoot(scope: ExportScope): RootNode {
  if (scope === 'PAGE') return figma.currentPage
  const selected = figma.currentPage.selection[0]
  if (!selected) throw new Error('Select a Frame or node before syncing.')
  return selected
}

function countNodes(root: BaseNode, limit: number): number {
  let count = 0
  const stack: BaseNode[] = [root]
  while (stack.length > 0 && count < limit) {
    const current = stack.pop()!
    count += 1
    if ('children' in current) {
      for (let index = current.children.length - 1; index >= 0; index -= 1) {
        stack.push(current.children[index])
      }
    }
  }
  return count
}

async function serializeDesignRoot(root: RootNode, warnings: string[]): Promise<Record<string, unknown>> {
  if (root.type !== 'PAGE') {
    try {
      const response = await root.exportAsync({ format: 'JSON_REST_V1' }) as { document?: Record<string, unknown> }
      if (response.document) return response.document
      warnings.push('JSON_REST_V1 returned no document; the compatibility serializer was used.')
    } catch (error) {
      warnings.push(`JSON_REST_V1 export failed; the compatibility serializer was used: ${errorMessage(error)}`)
    }
  }
  return serializeNode(root)
}

function serializeNode(node: RootNode | SceneNode): Record<string, unknown> {
  const result: Record<string, unknown> = {
    id: node.id,
    name: node.name,
    type: node.type === 'PAGE' ? 'CANVAS' : node.type,
  }

  const visible = read<boolean>(node, 'visible')
  if (visible !== undefined) result.visible = visible

  const bounds = read<Rect | null>(node, 'absoluteBoundingBox')
  if (bounds) {
    result.absoluteBoundingBox = {
      x: bounds.x,
      y: bounds.y,
      width: bounds.width,
      height: bounds.height,
    }
  }

  copyProperty(node, result, 'layoutMode')
  copyProperty(node, result, 'layoutWrap')
  copyProperty(node, result, 'itemSpacing')
  copyProperty(node, result, 'paddingTop')
  copyProperty(node, result, 'paddingRight')
  copyProperty(node, result, 'paddingBottom')
  copyProperty(node, result, 'paddingLeft')
  copyProperty(node, result, 'primaryAxisAlignItems')
  copyProperty(node, result, 'counterAxisAlignItems')
  copyProperty(node, result, 'primaryAxisSizingMode')
  copyProperty(node, result, 'counterAxisSizingMode')
  copyProperty(node, result, 'layoutSizingHorizontal')
  copyProperty(node, result, 'layoutSizingVertical')
  copyProperty(node, result, 'layoutPositioning')
  copyProperty(node, result, 'layoutGrow')
  copyProperty(node, result, 'layoutAlign')
  copyProperty(node, result, 'minWidth')
  copyProperty(node, result, 'maxWidth')
  copyProperty(node, result, 'minHeight')
  copyProperty(node, result, 'maxHeight')
  copyProperty(node, result, 'opacity')
  copyProperty(node, result, 'cornerRadius')
  copyProperty(node, result, 'strokeWeight')

  const constraints = read<Constraints>(node, 'constraints')
  if (constraints) result.constraints = clone(constraints)
  const fills = read<readonly Paint[] | symbol>(node, 'fills')
  if (Array.isArray(fills)) result.fills = clone(fills)
  const strokes = read<readonly Paint[] | symbol>(node, 'strokes')
  if (Array.isArray(strokes)) result.strokes = clone(strokes)

  if (node.type === 'TEXT') {
    result.characters = node.characters
    const style: Record<string, unknown> = {}
    const fontName = node.fontName
    if (fontName !== figma.mixed) style.fontFamily = fontName.family
    if (node.fontSize !== figma.mixed) style.fontSize = node.fontSize
    if (node.fontWeight !== figma.mixed) style.fontWeight = node.fontWeight
    if (node.lineHeight !== figma.mixed && node.lineHeight.unit === 'PIXELS') {
      style.lineHeightPx = node.lineHeight.value
    }
    if (node.letterSpacing !== figma.mixed && node.letterSpacing.unit === 'PIXELS') {
      style.letterSpacing = node.letterSpacing.value
    }
    result.style = style
  }

  if ('children' in node) {
    result.children = node.children
      .filter((child): child is SceneNode => child.type !== 'SLICE')
      .map((child) => serializeNode(child))
  }
  return result
}

async function exportAssets(root: RootNode, warnings: string[]): Promise<BridgeAsset[]> {
  const candidates: SceneNode[] = []
  walkSceneNodes(root, (node) => {
    if (shouldExportAsAsset(node)) candidates.push(node)
  })

  const assets: BridgeAsset[] = []
  const exportedIds = new Set<string>()
  for (let index = 0; index < candidates.length; index += 1) {
    const node = candidates[index]
    if (exportedIds.has(node.id)) continue
    exportedIds.add(node.id)
    try {
      const vector = isVectorNode(node)
      const extension = vector ? 'svg' : 'png'
      const contentType = vector ? 'image/svg+xml' : 'image/png'
      const bytes = await node.exportAsync(vector
        ? { format: 'SVG', svgOutlineText: false }
        : { format: 'PNG', constraint: { type: 'SCALE', value: 1 } })
      assets.push({
        nodeId: node.id,
        fileName: `${sanitizeFileName(node.name)}-${sanitizeId(node.id)}.${extension}`,
        contentType,
        bytes,
      })
      if (index % 10 === 0) {
        figma.ui.postMessage({
          type: 'asset-progress',
          current: index + 1,
          total: candidates.length,
        })
      }
    } catch (error) {
      warnings.push(`Asset ${node.name} (${node.id}) could not be exported: ${errorMessage(error)}`)
    }
  }
  return assets
}

async function exportReference(root: RootNode, warnings: string[]): Promise<Uint8Array | null> {
  if (root.type === 'PAGE') return null
  try {
    const bounds = root.absoluteBoundingBox
    const constraint: ExportSettingsConstraints = bounds && bounds.width > 1600
      ? { type: 'WIDTH', value: 1600 }
      : { type: 'SCALE', value: 1 }
    return await root.exportAsync({ format: 'PNG', constraint })
  } catch (error) {
    warnings.push(`Reference image could not be exported: ${errorMessage(error)}`)
    return null
  }
}

function walkSceneNodes(root: RootNode, visitor: (node: SceneNode) => void): void {
  const stack: BaseNode[] = [root]
  while (stack.length > 0) {
    const current = stack.pop()!
    if (current.type !== 'PAGE') visitor(current as SceneNode)
    if ('children' in current) {
      for (let index = current.children.length - 1; index >= 0; index -= 1) {
        stack.push(current.children[index])
      }
    }
  }
}

function shouldExportAsAsset(node: SceneNode): boolean {
  if (!('exportAsync' in node)) return false
  if (isVectorNode(node)) return true
  const fills = read<readonly Paint[] | symbol>(node, 'fills')
  return Array.isArray(fills) && fills.some((paint) => paint.visible !== false && paint.type === 'IMAGE')
}

function isVectorNode(node: SceneNode): boolean {
  return ['VECTOR', 'BOOLEAN_OPERATION', 'STAR', 'LINE', 'POLYGON'].includes(node.type)
}

function copyProperty(node: BaseNode, output: Record<string, unknown>, property: string): void {
  const value = read<unknown>(node, property)
  if (value !== undefined && value !== figma.mixed) output[property] = clone(value)
}

function read<T>(node: BaseNode, property: string): T | undefined {
  if (!(property in node)) return undefined
  return (node as unknown as Record<string, T>)[property]
}

function clone<T>(value: T): T {
  if (value === undefined || typeof value === 'symbol') return value
  return JSON.parse(JSON.stringify(value)) as T
}

function sanitizeFileName(value: string): string {
  const normalized = value.toLowerCase().trim()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/(^-|-$)/g, '')
  return normalized.slice(0, 80) || 'asset'
}

function sanitizeId(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/(^-|-$)/g, '') || 'node'
}

function normalizeBackendUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, '')
  const parsed = new URL(normalized)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Backend URL must use HTTP or HTTPS.')
  return normalized
}

function progress(label: string, step: number): void {
  figma.ui.postMessage({ type: 'progress', label, step })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
