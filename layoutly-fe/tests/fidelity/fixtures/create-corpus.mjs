// Original, authored test data. CC0-1.0. Never generates reference images.
// Run explicitly to create a NEW corpus; existing files are never overwritten.
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const out = path.dirname(fileURLToPath(import.meta.url))
const box = (x,y,width,height) => ({x,y,width,height})
const solid = (r=.15,g=.35,b=.65) => [{type:'SOLID',color:{r,g,b,a:1}}]
const rect = (id,x,y,w,h,extra={}) => ({id,name:id,type:'RECTANGLE',absoluteBoundingBox:box(x,y,w,h),fills:solid(),...extra})
const frame = (id,x,y,w,h,children=[],extra={}) => ({...rect(id,x,y,w,h),type:'FRAME',fills:solid(.96,.97,.99),children,...extra})
const text = (id,x,y,w,h,characters,extra={}) => ({...rect(id,x,y,w,h),type:'TEXT',characters,style:{fontFamily:'Inter',fontWeight:400,fontSize:16,lineHeightPx:24},...extra})
const root = (children=[],extra={}) => frame('root',0,0,800,600,children,extra)
const cases = []
const add = (id,category,tree,extra={}) => cases.push({id,category,tree,...extra})
add('landing','landing-sections',root([frame('hero',0,0,800,200,[text('headline',30,40,700,48,'Layoutly original fixture')]),frame('features',0,200,800,200),frame('footer',0,400,800,200)]))
const shop = frame('shop',0,0,800,600,[rect('product',30,60,260,300)])
const cart = frame('cart',1000,0,800,600,[text('cart-title',1030,40,500,48,'Your cart')])
add('commerce','ecommerce-multipage',{id:'root',type:'CANVAS',name:'Shop pages',children:[shop,cart]}, {displayRootId:'shop',expectedPages:2})
add('dashboard','dashboard-cards',root(Array.from({length:12},(_,i)=>frame(`card-${i}`,20+(i%4)*195,20+Math.floor(i/4)*180,175,160,[rect(`chart-${i}`,30+(i%4)*195,70+Math.floor(i/4)*180,150,80)]))))
add('form','forms',root([frame('login',180,80,440,440,[text('label',210,110,350,30,'Đăng nhập'),rect('field',210,180,350,48),rect('submit',210,280,350,48)])]))
add('nested-auto','nested-auto-layout',root([frame('column',0,0,600,500,[frame('row',0,0,600,80,[rect('item-a',0,0,80,80),rect('item-b',92,0,80,80)],{layoutMode:'HORIZONTAL',itemSpacing:12})],{layoutMode:'VERTICAL',itemSpacing:16})]))
add('grid','grid-spans',root([rect('span',0,0,400,180,{gridColumnSpan:2}),rect('cell',420,0,180,180)],{layoutMode:'GRID',gridColumnCount:3,gridRowCount:2,gridColumnGap:20,gridRowGap:20}))
add('negative-gap','negative-spacing',root([rect('overlap-a',0,0,140,100),rect('overlap-b',120,0,140,100),rect('overlap-c',240,0,140,100)],{layoutMode:'HORIZONTAL',itemSpacing:-20}))
add('overlays','absolute-overlays',root([frame('row',0,0,800,300,[rect('flow',0,0,200,100),rect('badge',170,10,50,50,{layoutPositioning:'ABSOLUTE'})],{layoutMode:'HORIZONTAL'})]))
add('mixed-text','mixed-typography',root([text('mixed',30,40,600,100,'Mixed bold regular text',{characterStyleOverrides:[0,0,1,1],styleOverrideTable:{1:{fontWeight:700,fontSize:24}}})]))
add('effects','gradients-blur-shadows',root([rect('effect',30,30,500,300,{fills:[{type:'GRADIENT_LINEAR',gradientHandlePositions:[{x:0,y:0},{x:1,y:1},{x:0,y:1}],gradientStops:[{position:0,color:{r:1,g:0,b:0,a:1}},{position:1,color:{r:0,g:0,b:1,a:1}}]}],effects:[{type:'DROP_SHADOW',offset:{x:0,y:8},radius:12,color:{r:0,g:0,b:0,a:.3}},{type:'LAYER_BLUR',radius:8}]})]))
add('masks','masks-clips',root([frame('clip',30,30,200,200,[rect('mask',40,40,120,120,{isMask:true}),rect('content',20,20,300,300)],{clipsContent:true})]))
add('vectors','svg-vectors',root([rect('vector',30,30,200,200,{type:'VECTOR',fillGeometry:[{path:'M0 0L100 0L50 100Z',windingRule:'NONZERO'}]})]),{assets:{vector:'triangle.svg'}})
add('image-children','image-filled-frame-children',root([frame('photo-frame',20,20,300,240,[text('caption',40,40,250,60,'Caption over image')],{fills:[{type:'IMAGE',imageRef:'authored-test',scaleMode:'FILL'}]})]),{assets:{'photo-frame':'triangle.svg'},assetProvenance:'rasterized-subtree'})
add('responsive','desktop-tablet-mobile',root([rect('wide',20,20,760,180,{constraints:{horizontal:'LEFT_RIGHT',vertical:'TOP'}})]),{viewports:[1440,1280,1024,768,430,390,375],expectedLayout:'LEGACY_SCALED_FIXED'})
const link = rect('next',30,40,200,60,{interactions:[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',navigation:'NAVIGATE',destinationId:'second'}]}]})
add('navigation','prototype-navigation',{id:'root',name:'Prototype',type:'CANVAS',children:[frame('first',0,0,800,600,[link]),frame('second',1000,0,800,600,[text('destination',1030,40,400,40,'Destination')])]}, {displayRootId:'first',expectedPages:2,expectedNavigation:[{from:'next',to:'second'}]})
add('components','component-instances-variants',root([frame('instance',30,30,300,200,[text('variant-text',50,50,240,40,'Primary variant')],{type:'INSTANCE',componentId:'component-1',componentProperties:{State:{type:'VARIANT',value:'Active'}}})]))
for (const [id,count] of [['medium',499],['large',1999]]) add(id,'large-node-tree',root(Array.from({length:count},(_,i)=>rect(`n-${i}`,5+(i%40)*19,5+Math.floor(i/40)*11,16,8))))
add('transforms','nested-transforms',root([frame('rotated',100,100,200,200,[rect('nested',130,130,80,80,{rotation:-.2})],{rotation:.4,relativeTransform:[[.92,-.39,100],[.39,.92,100]]})]))
add('unicode','long-unicode-vietnamese',root([text('vietnamese',24,24,360,350,'Tiếng Việt: thiết kế, xuất mã, đăng nhập. 👋 日本語\nDòng thứ hai — nội dung dài để kiểm tra xuống dòng '.repeat(4))]))
add('missing','missing-font-assets',root([text('unknown-font',30,30,500,100,'Unavailable font',{style:{fontFamily:'Layoutly Deliberately Missing Font',fontSize:32,fontWeight:400}}),rect('missing-image',30,180,300,300,{fills:[{type:'IMAGE',imageRef:'missing',scaleMode:'FILL'}]})]))
add('fixed-flex','fixed-flex-overflow',root([rect('fixed-a',0,0,500,100),rect('fixed-b',520,0,500,100)],{layoutMode:'HORIZONTAL',itemSpacing:20}))
await mkdir(out,{recursive:true})
const manifest=[]
for (let i=0;i<cases.length;i++) {
  const {tree,...spec}=cases[i]
  const dir=path.join(out,spec.id)
  await mkdir(dir,{recursive:true})
  const raw={name:spec.id,version:'layoutly-authored-v1',document:{id:'document',type:'DOCUMENT',children:[tree]}}
  await writeFile(path.join(dir,'figma-raw.json'),JSON.stringify(raw,null,2)+'\n',{flag:'wx'})
  if(spec.assets) await writeFile(path.join(dir,'triangle.svg'),'<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><path d="M0 0L200 0L100 200Z" fill="#2563eb"/></svg>',{flag:'wx'})
  manifest.push({schemaVersion:1,...spec,set:i%5===3?'validation':i%5===4?'holdout':'development',license:'CC0-1.0',provenance:'original-authored-synthetic-not-exported-from-Figma',sourceRevision:raw.version,raw:`${spec.id}/figma-raw.json`,rootId:'root',viewport:{width:800,height:600},reference:null,fontManifest:[]})
}
await writeFile(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'})
console.log(`Created ${manifest.length} original cases. No Figma reference PNGs supplied.`)
