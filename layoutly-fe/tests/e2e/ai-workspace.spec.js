import { expect, test } from '@playwright/test'
const realSnapshot = 'a93ff7f5-c887-48cf-8990-2c3b735fa794'

test('real generated React workspace compiles and renders in the isolated preview', async ({ page }) => {
  const errors=[]
  page.on('pageerror', err=>errors.push(err.message))
  // Restore a baseline view without creating or accepting any revision on disk.
  await page.route('**/ai/revisions',route=>route.fulfill({json:[{revisionId:'rev_000_baseline',status:'ACCEPTED',targetFramework:'REACT',modifiedFiles:[]}]}))
  await page.goto(`/?snapshotId=${realSnapshot}`)
  const frame=page.frameLocator('iframe[title="Snapshot preview"]')
  await expect(frame.locator('.figma-node').first()).toBeVisible({timeout:30000})
  await expect(frame.locator('body')).toContainText('Be prepared')
  await expect(page.locator('.we-preview-source')).toContainText('rev_000_baseline')
  expect(errors).toEqual([])
  await page.getByTitle('Reload preview',{exact:true}).click()
  await expect(frame.locator('.figma-node').first()).toBeVisible()
  expect(await frame.locator('body').evaluate(()=>{try{return !!parent.document.body}catch{return false}})).toBe(false)
})

test('draft diff, before/after, accept refresh, persistent history and accepted export', async ({ page },testInfo) => {
  await page.setViewportSize({width:1536,height:1024})
  let accepted=false,rejected=false,exportQuery=''
  const id='fixture-workspace'
  const rev={revisionId:'rev_001_test',targetFramework:'REACT',status:'DRAFT',modifiedFiles:['src/App.jsx'],buildSuccess:true,message:'Đã cập nhật nút chính.',diffs:[{filePath:'src/App.jsx',changeType:'MODIFIED',patchUnified:'--- a/src/App.jsx\n+++ b/src/App.jsx\n-<button>Before</button>\n+<button>After</button>'}]}
  await page.route(`**/api/snapshots/${id}**`,async route=>{
    const url=new URL(route.request().url()),path=url.pathname
    if(path.endsWith('/ai/status')) return route.fulfill({json:{available:true,activeProvider:'ollama',activeModel:'qwen-coder',providers:[{id:'ollama',displayName:'Local AI',configured:true,models:[{id:'qwen-coder',name:'Qwen Coder',supportsTools:true}]}]}})
    if(path.endsWith('/ai/revisions')) return route.fulfill({json:accepted?[{...rev,status:'ACCEPTED'}]:rejected?[{...rev,status:'REJECTED'}]:[]})
    if(path.endsWith('/ai/accepted')) return route.fulfill({json:accepted?{...rev,status:'ACCEPTED'}:null})
    if(path.endsWith('/ai/chat')) return route.fulfill({json:rev})
    if(path.endsWith('/diff')) return route.fulfill({json:rev.diffs})
    if(path.endsWith('/accept')) {accepted=true;return route.fulfill({json:{...rev,status:'ACCEPTED'}})}
    if(path.endsWith('/reject')) {rejected=true;return route.fulfill({json:{...rev,status:'REJECTED'}})}
    if(path.endsWith('/ai/files')) return route.fulfill({json:['index.html','src/main.jsx','src/App.jsx','src/styles/layoutly.css']})
    if(path.endsWith('/ai/file')) {
      const after=url.searchParams.get('revisionId')==='rev_001_test'
      const sources={'src/main.jsx':"import React from 'react';import {createRoot} from 'react-dom/client';import App from './App.jsx';createRoot(document.getElementById('root')).render(<App/>);",'src/App.jsx':`import React from 'react';import './styles/layoutly.css';export default function App(){return <main><button className="figma-node" id="node-1-2">${after?'After':'Before'}</button></main>}`,'src/styles/layoutly.css':'body{font-family:Arial;padding:32px}button{background:#f5b71b;border:0;border-radius:16px;padding:18px 32px;color:#102638}', 'index.html':'<div id="root"></div>'}
      return route.fulfill({body:sources[url.searchParams.get('path')]||'',contentType:'text/plain'})
    }
    if(path.endsWith('/ai/export')) {exportQuery=url.search;return route.fulfill({body:Buffer.from('PK-test-download-contract'),contentType:'application/zip',headers:{'content-disposition':'attachment; filename="accepted.zip"'}})}
    if(path.endsWith('/tree'))return route.fulfill({json:{id:'1:2',name:'HeroButton',type:'FRAME',children:[]}})
    if(path.endsWith('/preview'))return route.fulfill({body:'<html><body><button class="figma-node" id="node-1-2">Before</button></body></html>',contentType:'text/html'})
    if(/\/(html|css|react)$/.test(path))return route.fulfill({body:'Baseline source',contentType:'text/plain'})
    return route.fulfill({json:{snapshotId:id,fileName:'Revision lifecycle test fixture',source:'FIGMA_PLUGIN'}})
  })
  await page.goto(`/?snapshotId=${id}`)
  await page.getByRole('combobox',{name:'AI model'}).selectOption('ollama::qwen-coder')
  await page.getByLabel('Yêu cầu AI',{exact:true}).fill('Đổi màu nút chính')
  await page.getByTitle('Gui yeu cau AI').click()
  await expect(page.getByText('Static checks passed',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Xem diff',exact:true}).click()
  await expect(page.getByRole('dialog',{name:'Code diff'})).toContainText('+<button>After</button>')
  await page.getByRole('button',{name:'Đóng diff'}).click()
  await page.getByRole('button',{name:'Xem trước',exact:true}).click()
  const frame=page.frameLocator('iframe[title="Snapshot preview"]')
  await expect(frame.getByRole('button',{name:'After',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Before',exact:true}).click()
  await expect(frame.getByRole('button',{name:'Before',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'After',exact:true}).click()
  await expect(frame.getByRole('button',{name:'After',exact:true})).toBeVisible()
  await page.screenshot({path:testInfo.outputPath('draft-card-test-fixture-1536.png')})
  await page.getByRole('button',{name:'Chấp nhận',exact:true}).click()
  await expect(page.locator('.we-preview-source')).toContainText('ACCEPTED')
  await expect(frame.getByRole('button',{name:'After',exact:true})).toBeVisible()
  await page.getByRole('navigation',{name:'Project files'}).getByRole('button',{name:'src/App.jsx',exact:true}).click()
  await expect(page.locator('.we-code-document pre')).toContainText('After')
  await page.getByRole('button',{name:'Xuất dự án',exact:true}).click()
  await expect(page.getByRole('button',{name:'HTML/CSS ZIP',exact:true})).toBeDisabled()
  const downloading=page.waitForEvent('download')
  await page.getByRole('button',{name:'React ZIP',exact:true}).click()
  await downloading
  expect(exportQuery).toContain('revisionId=rev_001_test')
  await page.reload()
  await expect(page.locator('.we-preview-source')).toContainText('ACCEPTED')
  await expect(frame.getByRole('button',{name:'After',exact:true})).toBeVisible()
})

test('panel state, resize and short mobile composer remain usable',async({page})=>{
  await page.setViewportSize({width:1536,height:1024})
  await page.goto(`/?snapshotId=${realSnapshot}`)
  await expect(page.getByRole('complementary',{name:'Layoutly AI Assistant'})).toBeVisible()
  await page.getByRole('separator',{name:'Thay đổi chiều rộng AI'}).focus()
  await page.keyboard.press('ArrowLeft')
  await expect(page.locator('.we-ai')).toHaveCSS('width','450px')
  const aiHandle=await page.getByRole('separator',{name:'Thay đổi chiều rộng AI'}).boundingBox()
  await page.mouse.move(aiHandle.x+4,aiHandle.y+150);await page.mouse.down();await page.mouse.move(aiHandle.x-16,aiHandle.y+150);await page.mouse.up()
  await expect(page.locator('.we-ai')).toHaveCSS('width','470px')
  const resize=page.getByRole('separator',{name:'Thay đổi chiều cao code'})
  await resize.focus();await page.keyboard.press('ArrowUp')
  await expect(page.locator('.we-code-panel')).toHaveCSS('height','306px')
  await page.getByRole('button',{name:'Đóng AI Chat'}).click()
  await expect(page.locator('.we-ai-slot')).toBeHidden()
  await page.getByRole('button',{name:'Mở hoặc đóng Layoutly AI'}).click()
  await page.getByRole('button',{name:'Đổi màu nút',exact:true}).first().click()
  await expect(page.getByLabel('Yêu cầu AI',{exact:true})).toHaveValue(/#F59E0B/)
  await page.getByRole('button',{name:'Đóng AI Chat'}).click()
  await page.getByRole('button',{name:'Mở hoặc đóng Layoutly AI'}).click()
  await expect(page.getByLabel('Yêu cầu AI',{exact:true})).toHaveValue(/#F59E0B/)
  await page.setViewportSize({width:390,height:568})
  const composer=await page.locator('.we-composer').boundingBox()
  expect(composer.y+composer.height).toBeLessThanOrEqual(568)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.getByTitle('Mở hoặc thu gọn code',{exact:true}).click()
  await page.getByTitle('Mở hoặc thu gọn code',{exact:true}).click()
  await expect(page.getByRole('region',{name:'Mã nguồn sinh ra'})).toBeVisible()
})
