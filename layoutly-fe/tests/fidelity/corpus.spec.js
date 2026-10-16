import {test,expect} from '@playwright/test'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {readFileSync} from 'node:fs'
import path from 'node:path'
import {createHash} from 'node:crypto'
import {comparePng,summarize,fidelityEligibility,referenceEvidence,gates} from './metrics.mjs'
const output=path.resolve(process.env.LAYOUTLY_FIDELITY_OUTPUT||'../.local-build/fidelity/before')
const fixturesRoot=path.resolve('tests/fidelity/fixtures')
const fixtures=JSON.parse(readFileSync(path.join(output,'index.json'),'utf8'))
const flatten=n=>[n,...(n.children||[]).flatMap(flatten)]
const hash=b=>createHash('sha256').update(b).digest('hex')
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))

for(const spec of fixtures) test(`${spec.set}: ${spec.id}`,async({browser},testInfo)=>{
  const dir=path.join(output,spec.id), artifacts=path.join(dir,'measurement')
  // A repeat must use a fresh run directory, never rewrite reviewed baseline artefacts.
  await mkdir(artifacts)
  const original=await readFile(path.join(fixturesRoot,spec.raw))
  expect(hash(original)).toBe(spec.sourceSha256)
  const raw=JSON.parse(original), ir=JSON.parse(await readFile(path.join(dir,'design-ir.json'),'utf8'))
  const sourceNodes=flatten(raw.document), display=sourceNodes.find(n=>n.id===(spec.displayRootId||spec.rootId))
  const sourceBounds=display.absoluteBoundingBox
  const expected=flatten(display).filter(n=>n.absoluteBoundingBox && n.visible!==false)
  const unsupported=JSON.parse(await readFile(path.join(dir,'diagnostics.json'),'utf8')).diagnostics
  const captures={},screenshots={}
  for(const format of ['html','react']) {
    const context=await browser.newContext({viewport:spec.viewport,deviceScaleFactor:1,locale:'vi-VN',timezoneId:'Asia/Bangkok',colorScheme:'light',reducedMotion:'reduce',extraHTTPHeaders:{'X-Layoutly-Fixture':spec.id,'X-Layoutly-Format':format}})
    const page=await context.newPage(),runtimeErrors=[],failedRequests=[]
    page.on('pageerror',e=>runtimeErrors.push(e.message))
    page.on('requestfailed',r=>failedRequests.push({url:r.url(),error:r.failure()?.errorText}))
    page.on('response',r=>{if(r.status()>=400)failedRequests.push({url:r.url(),status:r.status()})})
    // Test fixtures are offline: any external request is a completeness error.
    await page.route('**/*',route=>new URL(route.request().url()).origin==='http://127.0.0.1:59991'?route.continue():route.abort('blockedbyclient'))
    await page.addInitScript(()=>{
      window.__measurement={longTasks:[]}
      new PerformanceObserver(list=>{window.__measurement.longTasks.push(...list.getEntries().map(e=>e.duration))}).observe({type:'longtask',buffered:true})
    })
    const url='http://127.0.0.1:59991/'+(format==='html'?spec.htmlFiles[0]:'')
    const samples=[],firstPaints=[]
    const repeats=['fixed-flex','medium','large'].includes(spec.id)?Number(process.env.LAYOUTLY_FIDELITY_SAMPLES||20):1
    let dom
    for(let i=0;i<repeats;i++) {
      await page.goto(url,{waitUntil:'load'})
      await page.evaluate(async()=>{
        await document.fonts.ready
        await Promise.all([...document.images].map(img=>img.decode().catch(()=>null)))
        await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))
      })
      const timing=await page.evaluate(()=>({stable:performance.now(),fcp:performance.getEntriesByName('first-contentful-paint')[0]?.startTime??null}))
      samples.push(timing.stable);if(timing.fcp!==null)firstPaints.push(timing.fcp)
      if(i===0) {
        screenshots[format]=await page.screenshot({path:path.join(artifacts,`${format}.png`),animations:'disabled'})
        dom=await page.evaluate(()=>{
          const rect=r=>({x:r.x,y:r.y,width:r.width,height:r.height})
          const page=document.querySelector('.figma-page'), canvas=document.querySelector('.figma-canvas')
          return {page:page?rect(page.getBoundingClientRect()):null,designWidth:page?Number(page.style.getPropertyValue('--figma-width')):null,designHeight:page?Number(page.style.getPropertyValue('--figma-height')):null,scale:canvas?Number(getComputedStyle(canvas).transform.match(/^matrix\(([^,]+)/)?.[1]||1):1,nodes:[...document.querySelectorAll('.figma-node')].map(el=>{
            const style=getComputedStyle(el),range=document.createRange();range.selectNodeContents(el)
            return {id:[...el.classList].find(c=>c.startsWith('node-'))?.slice(5),...rect(el.getBoundingClientRect()),text:el.children.length?null:el.textContent,fontFamily:style.fontFamily,fontWeight:style.fontWeight,fontSize:style.fontSize,lineHeight:style.lineHeight,lines:el.children.length?null:[...new Set([...range.getClientRects()].map(r=>Math.round(r.y)))].length}
          }),fontFaces:[...document.fonts].map(f=>({family:f.family,weight:f.weight,style:f.style,status:f.status})),longTasks:window.__measurement.longTasks,heapBytes:performance.memory?.usedJSHeapSize??null,images:[...document.images].map(img=>({src:img.src,complete:img.complete&&img.naturalWidth>0}))}
        })
      }
    }
    const geometry=expected.map(n=>{
      const actual=dom.nodes.find(d=>d.id===n.id.replaceAll(':','-'))
      const b=n.absoluteBoundingBox,s=dom.scale,origin=dom.page||{x:0,y:0}
      const target={x:origin.x+(b.x-sourceBounds.x)*s,y:origin.y+(b.y-sourceBounds.y)*s,width:b.width*s,height:b.height*s}
      return {nodeId:n.id,expected:target,actual:actual||null,maxErrorPx:actual?Math.max(...['x','y','width','height'].map(k=>Math.abs(actual[k]-target[k]))):null}
    })
    const requiredFonts=flatten(ir).filter(n=>n.type==='TEXT'&&n.style?.fontFamily).map(n=>({family:n.style.fontFamily,weight:n.style.fontWeight||400,style:n.style.fontStyle?.toLowerCase().includes('italic')?'italic':'normal'}))
    const missingFonts=[...new Set(requiredFonts.filter(required=>!dom.fontFaces.some(f=>{
      const weights=f.weight==='normal'?[400]:f.weight==='bold'?[700]:f.weight.split(/\s+/).map(Number)
      const weightMatch=weights.length===2?required.weight>=weights[0]&&required.weight<=weights[1]:weights.includes(required.weight)
      return f.family.replaceAll('"','').replaceAll("'",'')===required.family&&f.style===required.style&&weightMatch&&f.status==='loaded'
    })).map(f=>`${f.family}/${f.weight}/${f.style}`))]
    const neededAssets=flatten(ir).filter(n=>n.asset?.exportAsImage)
    const missingAssets=neededAssets.filter(n=>!spec.assets?.[n.id]).map(n=>n.id)
    let responsive=[]
    if(spec.viewports)for(const width of spec.viewports){await page.setViewportSize({width,height:600});await page.waitForTimeout(50);responsive.push(await page.evaluate(()=>({viewport:innerWidth,pageWidth:document.querySelector('.figma-page')?.getBoundingClientRect().width,scale:getComputedStyle(document.querySelector('.figma-canvas')).transform,mode:'LEGACY_SCALED_FIXED',reflowVerified:false})))}
    const navigation=[]
    for(const link of spec.expectedNavigation||[]) {
      await page.goto(url)
      const fromUrl=page.url()
      const anchor=page.locator(`.node-${link.from}`)
      const href=await anchor.getAttribute('href')
      await anchor.click()
      if(href)await page.waitForURL(u=>u.href!==fromUrl)
      const destinationVisible=await page.locator(`.node-${link.to}`).isVisible()
      navigation.push({...link,href,url:page.url(),destinationVisible,changedPage:page.url()!==fromUrl})
      if(destinationVisible&&href){await page.goBack();await expect(page.locator(`.node-${link.from}`)).toBeVisible();await page.goForward();await expect(page.locator(`.node-${link.to}`)).toBeVisible();navigation.at(-1).forwardDestinationVisible=true}
    }
    const rootDimensions={expectedNative:{width:sourceBounds.width,height:sourceBounds.height},actualNative:{width:dom.designWidth,height:dom.designHeight},match:Math.abs(dom.designWidth-sourceBounds.width)<=1&&Math.abs(dom.designHeight-sourceBounds.height)<=1}
    captures[format]={dom,geometry,rootDimensions,missingFonts,missingAssets,runtimeErrors,failedRequests,navigation,responsive,performance:{localDocumentFcpMs:summarize(firstPaints),localDocumentStableMs:summarize(samples),cacheMode:'HTTP no-store; renderer input already local; browser/OS warmed across iterations',networkFigmaMs:null,apiPreviewMs:null,peakMemoryBytes:null,observedHeapBytes:dom.heapBytes,longTaskTotalMs:dom.longTasks.reduce((a,b)=>a+b,0)}}
    await context.close()
  }
  const parity=comparePng(screenshots.html,screenshots.react)
  const typographyParity=captures.html.dom.nodes.filter(n=>n.text!==null&&n.text!=='').map(n=>{
    const other=captures.react.dom.nodes.find(r=>r.id===n.id)
    const fields=['text','lines','fontFamily','fontWeight','fontSize','lineHeight']
    return {nodeId:n.id,html:n,react:other||null,differingFields:other?fields.filter(field=>n[field]!==other[field]):fields,figmaWrappingOracle:'UNAVAILABLE_WITHOUT_FIGMA_TEXT_LAYOUT_OR_VERIFIED_REFERENCE'}
  })
  const assetInventory=[]
  for(const [nodeId,file] of Object.entries(spec.assets||{})){
    const source=hash(await readFile(path.join(fixturesRoot,spec.id,file)))
    const html=hash(await readFile(path.join(dir,'html/assets',file)))
    const react=hash(await readFile(path.join(dir,'react/src/assets',file)))
    assetInventory.push({nodeId,file,sourceSha256:source,htmlSha256:html,reactSha256:react,match:source===html&&source===react,provenance:spec.assetProvenance||'authored-source-asset'})
    expect(source).toBe(html);expect(source).toBe(react)
  }
  if(parity.diff)await writeFile(path.join(artifacts,'parity-diff.png'),parity.diff)
  if(parity.overlay)await writeFile(path.join(artifacts,'parity-overlay.png'),parity.overlay)
  const referencePath=spec.reference?.path?path.resolve(fixturesRoot,spec.reference.path):null
  let reference=null
  if(referencePath){
    expect(referenceEvidence(spec)).toEqual({valid:true})
    const bytes=await readFile(referencePath)
    expect(hash(bytes),'Reference hash must be explicitly reviewed in manifest').toBe(spec.reference.sha256)
    reference={}
    await writeFile(path.join(artifacts,'reference.png'),bytes)
    for(const format of ['html','react']){const c=comparePng(bytes,screenshots[format]);reference[format]=c.metrics;if(c.diff)await writeFile(path.join(artifacts,`${format}-diff.png`),c.diff);if(c.overlay)await writeFile(path.join(artifacts,`${format}-overlay.png`),c.overlay)}
  }
  const geometryPass=['html','react'].every(f=>captures[f].geometry.every(g=>g.maxErrorPx!==null&&g.maxErrorPx<=gates.maxGeometryErrorPx))
  const eligibility=fidelityEligibility({reference:referencePath,missingFonts:[...captures.html.missingFonts,...captures.react.missingFonts],missingAssets:[...captures.html.missingAssets,...captures.react.missingAssets],unsupported,runtimeErrors:[...captures.html.runtimeErrors,...captures.react.runtimeErrors,...captures.html.failedRequests,...captures.react.failedRequests],geometryPass,rootDimensionsMatch:captures.html.rootDimensions.match&&captures.react.rootDimensions.match,parityPass:parity.metrics.pass})
  const result={schemaVersion:1,id:spec.id,set:spec.set,provenance:spec.provenance,sourceSha256:spec.sourceSha256,sourceRevision:spec.sourceRevision,rootId:spec.rootId,viewport:spec.viewport,nodes:spec.nodes,frames:spec.frames,browser:browser.version(),playwright:JSON.parse(readFileSync('node_modules/@playwright/test/package.json','utf8')).version,os:process.platform,deviceScaleFactor:1,mode:'LEGACY_SCALED_FIXED',fontManifest:spec.fontManifest,gates,stagesMs:spec.stagesMs,unsupported,captures,htmlReactParity:parity.metrics,typographyParity,assetInventory,reference,eligibility,fidelityScore:null,status:eligibility.eligible?(reference.html.pass&&reference.react.pass?'REFERENCE_GATES_PASS':'REFERENCE_REGRESSION'):'INSUFFICIENT_EVIDENCE'}
  await writeFile(path.join(artifacts,'metrics.json'),JSON.stringify(result,null,2))
  await writeFile(path.join(artifacts,'report.html'),`<!doctype html><meta charset="utf-8"><title>${esc(spec.id)}</title><style>body{font:16px system-ui;margin:24px;background:#eee}img{max-width:45%;border:1px solid #aaa;margin:8px}pre{white-space:pre-wrap}</style><h1>${esc(spec.id)} — ${result.status}</h1><p>Figma fidelity score: unavailable unless references, fonts, assets and supported features are verified.</p>${reference?'<h2>Figma reference</h2><img src="reference.png">':'<p>No Figma reference supplied.</p>'}<h2>HTML / React ZIP</h2><img src="html.png"><img src="react.png"><h2>HTML–React diff / overlay</h2><img src="parity-diff.png"><img src="parity-overlay.png"><pre>${esc(JSON.stringify(result,null,2))}</pre>`)
  await testInfo.attach('metrics',{path:path.join(artifacts,'metrics.json'),contentType:'application/json'})
  // Known unsupported/parity defects are measurements, never silently converted into passing fidelity gates.
  expect(captures.html.runtimeErrors).toEqual([])
  expect(captures.react.runtimeErrors).toEqual([])
  expect(captures.html.failedRequests).toEqual([])
  expect(captures.react.failedRequests).toEqual([])
  if(reference)expect(result.status).not.toBe('REFERENCE_REGRESSION')
  if(spec.cssCorrections && ['negative-gap','fixed-flex'].includes(spec.id))for(const format of ['html','react']){
    for(const g of captures[format].geometry)expect(g.maxErrorPx,`${format}/${g.nodeId} geometry`).not.toBeNull()
    expect(Math.max(...captures[format].geometry.map(g=>g.maxErrorPx))).toBeLessThanOrEqual(gates.maxGeometryErrorPx)
  }
  if(spec.pageConsistency && spec.expectedPages) {
    expect(spec.htmlFiles.length).toBe(spec.expectedPages)
    expect(spec.reactRoutes.length).toBe(spec.expectedPages)
    expect(parity.metrics.pass,'HTML and React must render the same selected page').toBe(true)
    for(const format of ['html','react'])for(const link of captures[format].navigation){expect(link.href).toBeTruthy();expect(link.changedPage).toBe(true);expect(link.destinationVisible).toBe(true);expect(link.forwardDestinationVisible).toBe(true)}
  }
  expect(hash(await readFile(path.join(fixturesRoot,spec.raw)))).toBe(spec.sourceSha256)
})
