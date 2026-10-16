import {test,expect} from '@playwright/test'
import {readFile,writeFile} from 'node:fs/promises'
import {summarize} from '../fidelity/metrics.mjs'

test('real API preview stages and stable isolated browser render: small / 500 / 2000 nodes',async({page},testInfo)=>{
  test.setTimeout(180000)
  const csrf=(await (await page.request.get('/api/auth/csrf')).json()).token
  const registered=await page.request.post('/api/auth/register',{headers:{'X-CSRF-TOKEN':csrf},data:{displayName:'Preview profiler',email:`profile-${Date.now()}@example.com`,password:'ProfileSecure123!',acceptedTermsVersion:'1'}})
  expect(registered.ok(),`Register status ${registered.status()}`).toBe(true)
  const results=[]
  for(const id of ['fixed-flex','medium','large']){
    const token=(await (await page.request.get('/api/auth/csrf')).json()).token
    const paired=await page.request.post('/api/auth/bridge-token',{headers:{'X-CSRF-TOKEN':token}})
    expect(paired.ok()).toBe(true)
    const pairing=await paired.json()
    const raw=await readFile(new URL(`../fidelity/fixtures/${id}/figma-raw.json`,import.meta.url))
    const metadata={source:'FIGMA_PLUGIN',fileKey:`profile-${id}`,fileName:id,pageName:'Page',nodeId:'root',nodeName:'root',nodeType:'FRAME',exportedAt:new Date().toISOString(),assets:[],warnings:[]}
    const imported=await page.request.post('/api/bridge/import',{headers:{'X-Layoutly-Bridge-Token':pairing.token},multipart:{metadata:{name:'metadata.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(metadata))},designJson:{name:'design.json',mimeType:'application/json',buffer:raw}}})
    expect(imported.ok()).toBe(true)
    const snapshot=await imported.json()
    const api=[],stages=[],stable=[]
    // Local test document contains only an opaque-origin iframe, matching editor isolation.
    await page.goto('/')
    for(let i=0;i<20;i++){
      const start=performance.now()
      const preview=await page.request.get(`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=800&nodeId=root`)
      expect(preview.ok()).toBe(true)
      const html=await preview.text()
      api.push(performance.now()-start)
      stages.push(preview.headers()['server-timing'])
      expect(stages.at(-1)).toContain('css;dur=')
      expect(preview.headers()['x-layoutly-missing-assets']).toBe('0')
      expect(preview.headers()['x-layoutly-root-id']).toBe('root')
      expect(Number(preview.headers()['x-layoutly-node-count'])).toBe(id==='large'?2000:id==='medium'?500:3)
      const renderStart=performance.now()
      await page.evaluate(html=>{
        document.body.replaceChildren()
        const frame=document.createElement('iframe');frame.title='Profile preview';frame.sandbox='allow-scripts';frame.width='800';frame.height='600';frame.srcdoc=html;document.body.append(frame)
      },html)
      const iframe=page.frameLocator('iframe[title="Profile preview"]')
      await expect(iframe.locator('.node-root')).toBeVisible()
      await iframe.locator('body').evaluate(async()=>{await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))})
      stable.push(performance.now()-renderStart)
      if(i===0)await page.screenshot({path:testInfo.outputPath(`${id}-preview.png`)})
    }
    const diagnostics=await page.request.get(`/api/snapshots/${snapshot.snapshotId}/diagnostics`)
    expect(diagnostics.ok()).toBe(true)
    results.push({fixture:id,sourceReady:true,assetCount:0,fontCount:0,apiRequestMs:summarize(api),browserStableAfterResponseMs:summarize(stable),serverTiming:stages,figmaNetworkMs:null,definition:'20 local authenticated API requests plus opaque iframe rendering; includes Playwright transport overhead; source has no assets/fonts. Not representative of asset-heavy imports.'})
  }
  const report=testInfo.outputPath('real-preview-profile.json')
  await writeFile(report,JSON.stringify(results,null,2))
  await testInfo.attach('real-preview-profile.json',{path:report,contentType:'application/json'})
})
