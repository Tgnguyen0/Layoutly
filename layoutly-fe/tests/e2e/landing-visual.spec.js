import {test,expect} from '@playwright/test'
import {mkdir,writeFile} from 'node:fs/promises'
import path from 'node:path'
const round=process.env.LAYOUTLY_LANDING_ROUND||'round-1'
if(!/^[a-z0-9-]+$/.test(round))throw new Error('Invalid screenshot round')
const out=path.resolve('../.local-build/landing-redesign',round)
for(const width of [1440,1024,768,390])test(`landing layout and product imagery at ${width}px`,async({page})=>{
  await mkdir(out,{recursive:true})
  await page.setViewportSize({width,height:width===390?844:960})
  const errors=[],missing=[]
  page.on('pageerror',e=>errors.push(e.message))
  page.on('response',r=>{if(r.status()>=400)missing.push({url:r.url(),status:r.status()})})
  await page.goto('/')
  await expect(page.getByRole('heading',{level:1})).toContainText('Tạo code từ')
  for(const id of ['work','grow','showcase','demo','faq'])await expect(page.locator('#'+id)).toBeAttached()
  for(const image of await page.locator('img').all()){await image.scrollIntoViewIfNeeded();await image.evaluate(img=>img.decode())}
  await page.evaluate(()=>document.fonts.ready)
  const metrics=await page.evaluate(()=>({
    width:innerWidth,scrollWidth:document.documentElement.scrollWidth,
    missingImages:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src),
    clippedControls:[...document.querySelectorAll('.ly-landing-content a,.ly-landing-content button,.ly-landing-content h2,.ly-landing-content h3,.ly-site-footer a')].filter(el=>{const r=el.getBoundingClientRect();return r.width&&(r.x< -1||r.right>innerWidth+1)}).map(el=>el.textContent),
    sections:[...document.querySelectorAll('.ly-landing-content section')].map(el=>({id:el.id,top:el.offsetTop,height:el.offsetHeight})),
  }))
  await writeFile(path.join(out,`metrics-${width}.json`),JSON.stringify({...metrics,errors,missing},null,2))
  expect(metrics.scrollWidth).toBeLessThanOrEqual(width)
  expect(metrics.missingImages).toEqual([]);expect(metrics.clippedControls).toEqual([])
  expect(errors).toEqual([]);expect(missing).toEqual([])
  await page.evaluate(()=>scrollTo(0,0))
  await page.screenshot({path:path.join(out,`landing-${width}.png`),fullPage:true,animations:'disabled'})
  if(width===1440){await page.locator('#work').screenshot({path:path.join(out,'workflow.png')});await page.locator('#grow').screenshot({path:path.join(out,'features.png')});await page.locator('#demo').screenshot({path:path.join(out,'demo.png')})}
})
test('showcase, code tabs, FAQ, downloads and navigation work',async({page})=>{
  await page.goto('/')
  for(const [label,id] of [['E-commerce','commerce'],['Dashboard','dashboard'],['Form & tài khoản','forms'],['Landing page','atlas']]){
    await page.getByRole('tab',{name:label,exact:true}).click()
    await expect(page.locator('#lm-showcase-panel img')).toHaveAttribute('src',`/landing/showcase-${id}.png`)
    await page.locator('#lm-showcase-panel img').evaluate(img=>img.decode())
  }
  await page.getByRole('tab',{name:'Landing page',exact:true}).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab',{name:'E-commerce',exact:true})).toBeFocused()
  const snippets=[]
  for(const format of ['HTML','CSS','React']){await page.getByRole('tab',{name:format,exact:true}).click();await expect(page.locator('#lm-code-output pre')).toContainText('atlas-heading');snippets.push(await page.locator('#lm-code-output pre').innerText())}
  expect(new Set(snippets).size).toBe(3)
  await page.context().grantPermissions(['clipboard-read','clipboard-write'])
  await page.getByRole('button',{name:'Sao chép mã mẫu',exact:true}).click()
  await expect(page.getByRole('button',{name:'Đã sao chép mã mẫu',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>navigator.clipboard.readText())).toContain('Go somewhere')
  const faq=page.locator('#faq details').nth(1)
  await faq.locator('summary').click();await expect(faq).toHaveAttribute('open','')
  await faq.locator('summary').click();await expect(faq).not.toHaveAttribute('open','')
  for(const file of ['atlas-figma.json','atlas-html.zip','atlas-react.zip','atlas-code.json']){
    const response=await page.request.get('/landing/'+file);expect(response.ok()).toBe(true)
    if(file.endsWith('.zip'))expect((await response.body()).subarray(0,2).toString()).toBe('PK')
  }
  const download=page.waitForEvent('download')
  await page.getByRole('link',{name:'Tải mã mẫu React',exact:true}).click()
  expect((await download).suggestedFilename()).toBe('atlas-react.zip')
  for(const id of ['work','grow','showcase','demo','faq']){
    await page.goto('/#'+id)
    await expect.poll(()=>page.locator('#'+id).evaluate(el=>Math.abs(el.getBoundingClientRect().top))).toBeLessThan(80)
  }
  await page.goto('/')
  await page.getByRole('link',{name:'Tạo tài khoản miễn phí',exact:true}).click()
  await expect(page.getByLabel('Email',{exact:true})).toBeVisible()
  await page.goto('/#/terms')
  await page.getByRole('link',{name:'Demo & mã nguồn',exact:true}).click()
  await expect(page.locator('#lm-demo-title')).toBeVisible()
})
