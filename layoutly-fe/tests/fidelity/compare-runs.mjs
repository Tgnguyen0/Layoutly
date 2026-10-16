import {readFile,writeFile} from 'node:fs/promises'
import path from 'node:path'
import {comparePng} from './metrics.mjs'
const before=path.resolve(process.argv[2]||'../.local-build/fidelity/before')
const after=path.resolve(process.argv[3]||'../.local-build/fidelity/after')
const index=JSON.parse(await readFile(path.join(after,'index.json'),'utf8'))
const rows=[],regressions=[]
const maxGeometry=c=>Math.max(0,...c.geometry.map(g=>g.maxErrorPx??Infinity))
for(const spec of index){
  const a=JSON.parse(await readFile(path.join(before,spec.id,'measurement/metrics.json'),'utf8'))
  const b=JSON.parse(await readFile(path.join(after,spec.id,'measurement/metrics.json'),'utf8'))
  if(a.sourceSha256!==b.sourceSha256||a.browser!==b.browser||JSON.stringify(a.viewport)!==JSON.stringify(b.viewport))throw new Error(`Incomparable runs: ${spec.id}`)
  const formats={}
  for(const format of ['html','react']){
    const ac=a.captures[format],bc=b.captures[format]
    const diff=comparePng(await readFile(path.join(before,spec.id,`measurement/${format}.png`)),await readFile(path.join(after,spec.id,`measurement/${format}.png`)))
    if(diff.diff)await writeFile(path.join(after,spec.id,`measurement/before-after-${format}-diff.png`),diff.diff)
    formats[format]={maxGeometryBefore:maxGeometry(ac),maxGeometryAfter:maxGeometry(bc),stableBefore:ac.performance.localDocumentStableMs,stableAfter:bc.performance.localDocumentStableMs,changePixels:diff.metrics.pixelDiffRatio,missingFonts:bc.missingFonts,missingAssets:bc.missingAssets}
    if(maxGeometry(bc)>maxGeometry(ac)+1)regressions.push(`${spec.id}/${format}: geometry worsened`)
    if(bc.runtimeErrors.length||bc.failedRequests.length)regressions.push(`${spec.id}/${format}: runtime/resource failure`)
  }
  if(b.htmlReactParity.pixelDiffRatio>a.htmlReactParity.pixelDiffRatio+.001)regressions.push(`${spec.id}: HTML/React parity worsened`)
  rows.push({id:spec.id,set:spec.set,nodes:spec.nodes,fidelityScore:null,eligibility:b.eligibility,htmlReactParity:b.htmlReactParity,formats,unsupported:b.unsupported.map(d=>d.code)})
}
const report={schemaVersion:1,before,after,regressions,fixtures:rows,note:'Measurement collection success is not a Figma fidelity pass. No supplied Figma reference => score null. Timings measure local exported documents, not API/import/cached-preview latency.'}
await writeFile(path.join(after,'comparison.json'),JSON.stringify(report,null,2))
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;')
await writeFile(path.join(after,'comparison.html'),`<!doctype html><meta charset="utf-8"><title>Layoutly Phase A/B</title><style>body{font:15px system-ui;margin:24px}td,th{border:1px solid #ccc;padding:8px}table{border-collapse:collapse}img{max-width:45%;margin:8px}</style><h1>Layoutly Phase A / CSS corrections</h1><p>${esc(report.note)}</p><p>Regressions: ${esc(regressions.join('; ')||'none on measured gates')}</p><table><tr><th>Fixture</th><th>Figma score</th><th>HTML geometry max px before → after</th><th>HTML/React diff</th><th>Report</th></tr>${rows.map(r=>`<tr><td>${r.id}</td><td>Unavailable</td><td>${r.formats.html.maxGeometryBefore.toFixed(2)} → ${r.formats.html.maxGeometryAfter.toFixed(2)}</td><td>${(r.htmlReactParity.pixelDiffRatio*100).toFixed(3)}%</td><td><a href="${r.id}/measurement/report.html">details</a></td></tr>`).join('')}</table>${['negative-gap','fixed-flex'].map(id=>`<h2>${id}</h2><p>Before / after</p><img src="../${path.basename(before)}/${id}/measurement/html.png"><img src="${id}/measurement/html.png">`).join('')}`)
console.log(JSON.stringify({fixtures:rows.length,regressions},null,2))
if(regressions.length)process.exitCode=1
