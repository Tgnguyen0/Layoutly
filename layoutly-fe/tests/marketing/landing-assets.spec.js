// Opt-in asset capture; excluded from the ordinary E2E test directory.
import {test,expect} from '@playwright/test'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import path from 'node:path'
import {marketingFixtures} from '../support/marketing-fixtures.mjs'
test('capture real Layoutly Workspace, Dashboard and original converted demo fixtures',async({page})=>{
  test.setTimeout(180000)
  const assets=path.resolve('public/landing')
  await mkdir(assets,{recursive:true})
  await page.setViewportSize({width:1440,height:960})
  const csrf=async()=>({'X-CSRF-TOKEN':(await (await page.request.get('/api/auth/csrf')).json()).token})
  const registered=await page.request.post('/api/auth/register',{headers:await csrf(),data:{displayName:'Layoutly Studio',email:`landing-${Date.now()}@example.com`,password:'LandingStudioTest123!',acceptedTermsVersion:'1'}})
  expect(registered.status()).toBe(201)
  const projects=[]
  for(const fixture of marketingFixtures){
    const pairing=await page.request.post('/api/auth/bridge-token',{headers:await csrf()})
    expect(pairing.ok()).toBe(true)
    const token=(await pairing.json()).token
    const file=fixture.svg?Buffer.from(fixture.svg):await readFile('public/brand/sneaker.png')
    const mime=fixture.svg?'image/svg+xml':'image/png'
    const metadata={source:'FIGMA_PLUGIN',fileKey:`layoutly-original-${fixture.id}`,fileName:fixture.name,pageName:'Page',nodeId:'screen',nodeName:fixture.name,nodeType:'FRAME',exportedAt:new Date().toISOString(),assets:[{nodeId:fixture.assetNode,fileName:fixture.assetName,contentType:mime}],warnings:[]}
    const response=await page.request.post('/api/bridge/import',{headers:{'X-Layoutly-Bridge-Token':token},multipart:{metadata:{name:'metadata.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(metadata))},designJson:{name:'design.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(fixture.design))},assets:{name:fixture.assetName,mimeType:mime,buffer:file}}})
    expect(response.ok()).toBe(true)
    const snapshot=await response.json()
    const saved=await page.request.post('/api/projects',{headers:await csrf(),data:{name:fixture.name,framework:'REACT',snapshotId:snapshot.snapshotId,description:'Dự án mẫu tự biên soạn cho ảnh sản phẩm Layoutly.'}})
    expect(saved.ok()).toBe(true)
    projects.push((await saved.json()).project)
    await page.goto(`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=1440&nodeId=screen`)
    await expect(page.locator('.node-screen')).toBeVisible()
    await page.evaluate(()=>document.fonts.ready)
    await page.locator('.figma-page').screenshot({path:path.join(assets,`showcase-${fixture.id}.png`),animations:'disabled'})
    if(fixture.id==='atlas'){
      const code={}
      for(const format of ['html','css','react']){const result=await page.request.get(`/api/snapshots/${snapshot.snapshotId}/${format}`);expect(result.ok()).toBe(true);code[format]=await result.text()}
      await writeFile(path.join(assets,'atlas-code.json'),JSON.stringify(code,null,2))
      for(const format of ['HTML','REACT']){const exported=await page.request.get(`/api/snapshots/${snapshot.snapshotId}/export?format=${format}`);expect(exported.ok()).toBe(true);const bytes=await exported.body();expect(bytes.subarray(0,2).toString()).toBe('PK');await writeFile(path.join(assets,`atlas-${format.toLowerCase()}.zip`),bytes)}
      await writeFile(path.join(assets,'atlas-figma.json'),JSON.stringify(fixture.design,null,2))
    }
  }
  await page.setViewportSize({width:1360,height:960})
  await page.goto('/#/dashboard')
  await expect(page.locator('.db-card')).toHaveCount(4)
  await page.getByRole('link',{name:'Mở '+marketingFixtures[0].name,exact:true}).click()
  await expect(page.locator('.we-project-title h1')).toHaveText(marketingFixtures[0].name)
  await expect(page.frameLocator('iframe[title="Snapshot preview"]').locator('.node-atlas-heading')).toBeVisible()
  await page.getByRole('button',{name:'React',exact:true}).click()
  await expect(page.locator('.we-code-document pre')).toContainText('export default')
  await page.screenshot({path:path.join(assets,'workspace.png'),animations:'disabled'})
  await page.goto('/#/dashboard')
  await expect(page.locator('.db-card')).toHaveCount(4)
  for(const fixture of marketingFixtures)await expect(page.frameLocator(`iframe[title="Thumbnail ${fixture.name}"]`).locator('.node-screen')).toBeVisible()
  await page.screenshot({path:path.join(assets,'dashboard.png'),animations:'disabled'})
  await writeFile(path.join(assets,'capture-manifest.json'),JSON.stringify({capturedAt:new Date().toISOString(),origin:'Actual Layoutly UI and backend-generated previews; original authored demo data; no personal projects',sourceRevision:'layoutly-marketing-v1',fixtures:marketingFixtures.map(f=>({id:f.id,name:f.name,rootId:'screen'}))},null,2))
})
