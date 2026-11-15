figma.showUI(__html__, { width: 640, height: 720, themeColors: false });

// Khớp FigmaParserService.isVectorType / parseAsset
const VECTOR_TYPES = new Set(['VECTOR', 'BOOLEAN_OPERATION', 'STAR', 'LINE', 'REGULAR_POLYGON', 'POLYGON']);
const MAX_REFERENCE_BYTES = 15 * 1024 * 1024;

function sendSelection() {
  const node = figma.currentPage.selection[0];
  figma.ui.postMessage(node ? { type: 'selection', id: node.id, name: node.name } : { type: 'selection', empty: true });
}
figma.on('selectionchange', sendSelection);
sendSelection();

function collectAssetIds(n: any, out: string[]) {
  const hasImageFill = (n.fills ?? []).some((p: any) => p.type === 'IMAGE' && p.visible !== false);
  if (VECTOR_TYPES.has(n.type) || hasImageFill) out.push(n.id);
  for (const c of n.children ?? []) collectAssetIds(c, out);
}

// Backend: sanitizeId(id) + ".png", regex [a-zA-Z0-9][a-zA-Z0-9._-]*
const assetFileName = (id: string) => id.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/(^-|-$)/g, '') + '.png';

async function exportSelection(scope: 'SELECTION' | 'PAGE', requestId?: number) {
  const node: any = scope === 'PAGE' ? figma.currentPage : figma.currentPage.selection[0];
  if (!node) return figma.ui.postMessage({ type: 'export-error', requestId, message: 'Hãy chọn một Frame trong Figma.' });

  try {
    let json: any = await node.exportAsync({ format: 'JSON_REST_V1' });
    // Backend (generateAuto) cần cây Document -> Canvas -> Frame như REST API
    let doc = json.document ?? json;
    if (doc.type !== 'DOCUMENT') {
      const canvas = doc.type === 'CANVAS'
        ? doc
        : { id: figma.currentPage.id, name: figma.currentPage.name, type: 'CANVAS', children: [doc] };
      doc = { id: '0:0', name: 'Document', type: 'DOCUMENT', children: [canvas] };
    }
    json = { document: doc };

    const ids: string[] = [];
    collectAssetIds(json.document, ids);

    const assets: { nodeId: string; fileName: string; bytes: Uint8Array }[] = [];
    for (const id of ids) {
      const n = await figma.getNodeByIdAsync(id);
      if (!n || !('exportAsync' in n)) continue;
      const bytes = await n.exportAsync({ format: 'PNG', constraint: { type: 'SCALE', value: 2 } });
      assets.push({ nodeId: id, fileName: assetFileName(id), bytes });
    }

    // Ảnh export
    let reference: Uint8Array | null = null;
    if (scope === 'SELECTION') {
      try {
        const ref = await node.exportAsync({ format: 'PNG', constraint: { type: 'SCALE', value: 1 } });
        if (ref.length <= MAX_REFERENCE_BYTES) reference = ref;
      } catch { /* bỏ qua */ }
    }

    figma.ui.postMessage({
      type: 'export-data',
      meta: {
        source: 'FIGMA_PLUGIN',
        fileKey: null,
        fileName: figma.root.name,
        pageName: figma.currentPage.name,
        nodeId: node.id,
        nodeName: node.name,
        nodeType: node.type,
        exportedAt: new Date().toISOString(),
        assets: assets.map((a) => ({ nodeId: a.nodeId, fileName: a.fileName, contentType: 'image/png' })),
        warnings: [],
      },
      json,
      assets,
      reference,
    });
    figma.ui.postMessage({ type: 'export-data', requestId, meta: { /* giữ nguyên */ }, json, assets, reference });
  } catch (e) {
    figma.ui.postMessage({ type: 'export-error', requestId, message: String(e) });
  }
}

figma.ui.onmessage = (msg: { type: string; message?: string; scope?: 'SELECTION' | 'PAGE'; requestId?: number }) => {
  if (msg.type === 'notify' && msg.message) figma.notify(msg.message);
  if (msg.type === 'ready') sendSelection();
  if (msg.type === 'export') exportSelection(msg.scope === 'PAGE' ? 'PAGE' : 'SELECTION', msg.requestId);
  if (msg.type === 'close') figma.closePlugin();
};