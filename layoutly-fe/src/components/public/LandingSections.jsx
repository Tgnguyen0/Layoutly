import {useEffect, useState} from 'react'
import {ArrowDown, ArrowRight, ArrowUpRight, Braces, Check, CheckCheck, ChevronRight, Code2, Copy, Download, Figma, FolderOpen, Layers3, Monitor, Plus, ShieldCheck} from 'lucide-react'

const steps=[
  {icon:Figma,title:'Thiết kế trong Figma',text:'Bắt đầu từ file, frame và ý tưởng của bạn.'},
  {icon:FolderOpen,title:'Nhập thiết kế',text:'Dán liên kết Figma hoặc kết nối Plugin Bridge.'},
  {icon:Code2,title:'Preview & code',text:'Xem từng layer, giao diện và mã nguồn cùng lúc.'},
  {icon:Download,title:'Mang mã nguồn về',text:'Xuất project HTML/CSS hoặc React cùng assets.'},
]
const examples=[
  {id:'atlas',label:'Landing page',name:'Atlas — những hành trình đáng nhớ',text:'Một câu chuyện thương hiệu, những section có nhịp điệu và hình ảnh làm điểm nhấn.',tag:'Du lịch · Giới thiệu sản phẩm'},
  {id:'commerce',label:'E-commerce',name:'Sneaker — thiết kế cho mỗi ngày',text:'Từ bộ sưu tập đến trang giới thiệu sản phẩm. Kiểm tra bố cục trước khi nối dữ liệu bán hàng.',tag:'Bán lẻ · Sản phẩm'},
  {id:'dashboard',label:'Dashboard',name:'Forma — không gian cho công việc',text:'Cards, biểu đồ và những lớp thông tin trong một giao diện quản lý. Số liệu trong mẫu là minh họa.',tag:'Quản lý · Dữ liệu mẫu'},
  {id:'forms',label:'Form & tài khoản',name:'Serein — một khởi đầu nhẹ nhàng',text:'Khám phá cách một thiết kế biểu mẫu trở thành giao diện. Bạn tiếp tục kết nối logic ứng dụng.',tag:'Đăng nhập · Biểu mẫu'},
]
const questions=[
  ['Tôi cần chuẩn bị gì trước khi bắt đầu?','Một tài khoản Layoutly và thiết kế Figma bạn có quyền sử dụng. Bạn có thể nhập bằng URL/File Key với Figma token, hoặc dùng Plugin Bridge để gửi frame cùng assets vào Workspace.'],
  ['Layoutly xuất được những định dạng nào?','Bạn có thể xem HTML, CSS, React và cấu trúc thiết kế trong Workspace. Khi export, Layoutly đóng gói project HTML/CSS hoặc React thành ZIP cùng các assets đã nhập thành công.'],
  ['Tôi có thể quay lại dự án sau khi đăng xuất không?','Có. Lưu thiết kế thành dự án trong tài khoản của bạn, sau đó mở lại từ Dashboard để tiếp tục xem preview, mã nguồn và lịch sử xuất. Backend kiểm tra quyền truy cập của từng người dùng.'],
  ['Giao diện xuất ra có giống hệt thiết kế Figma không?','Kết quả phụ thuộc vào bố cục, font, assets và các thuộc tính của thiết kế. Hãy kiểm tra preview trước khi xuất. Những thiết kế có mask, hiệu ứng hoặc typography phức tạp có thể cần chỉnh sửa thêm; Layoutly không cam kết một tỷ lệ fidelity khi chưa đo với reference hợp lệ.'],
  ['Mã nguồn có tự hoạt động trên mọi kích thước màn hình không?','Bạn nên kiểm thử và điều chỉnh trên các kích thước mục tiêu. Co giãn preview không đồng nghĩa với responsive reflow. Bản xuất là điểm khởi đầu để bạn tiếp tục hoàn thiện giao diện và logic sản phẩm.'],
  ['Tôi có thể thử một bản xuất trước khi import thiết kế của mình không?','Có. Demo ngay trên trang này có mã trích từ fixture Atlas được Layoutly chuyển đổi, kèm file JSON nguồn và ZIP HTML/CSS hoặc React để tải về. Không cần đăng nhập để tải các mẫu công khai này.'],
]
function Shot({src,alt,className='',label='Layoutly Workspace'}) {
  return <figure className={`lm-product-shot ${className}`}><div className="lm-shot-chrome" aria-hidden="true"><span><i/><i/><i/></span><small>{label}</small><ArrowUpRight size={12}/></div><a href={src} target="_blank" rel="noreferrer" aria-label={`Mở ảnh ${label} kích thước đầy đủ`}><img src={src} alt={alt} loading="lazy" decoding="async" width="1360" height="960"/><span className="lm-shot-zoom"><ArrowUpRight size={12}/>Xem ảnh đầy đủ</span></a></figure>
}
function CodeExcerpt({text}) {
  const pattern=/("[^"\n]*"|'[^'\n]*'|\b(?:export|default|function|return|const|import|from)\b|<\/?[\w-]+|\b\d+(?:\.\d+)?\b)/g
  return <code>{text.split('\n').map((line,index)=><span className="lm-code-line" key={index}><i aria-hidden="true">{index+1}</i><span>{line.split(pattern).map((token,i)=>{
    const kind=/^["']/.test(token)?'string':/^<\/?/.test(token)?'tag':/^\d/.test(token)?'number':/^(export|default|function|return|const|import|from)$/.test(token)?'keyword':''
    return kind?<span className={'lm-token-'+kind} key={i}>{token}</span>:token
  })}</span>{'\n'}</span>)}</code>
}
function tabKeys(event,index,items,choose,prefix) {
  let next=index
  if(['ArrowRight','ArrowDown'].includes(event.key))next=(index+1)%items.length
  else if(['ArrowLeft','ArrowUp'].includes(event.key))next=(index-1+items.length)%items.length
  else if(event.key==='Home')next=0
  else if(event.key==='End')next=items.length-1
  else return
  event.preventDefault();choose(items[next]);document.getElementById(prefix+items[next].id)?.focus()
}
function ConversionDemo() {
  const [format,setFormat]=useState('react')
  const [source,setSource]=useState(null)
  const [copied,setCopied]=useState(false)
  const [notice,setNotice]=useState('')
  const [loadError,setLoadError]=useState('')
  useEffect(()=>{
    const controller=new AbortController()
    fetch('/landing/atlas-code.json',{signal:controller.signal}).then(r=>{if(!r.ok)throw new Error('Mã mẫu chưa tải được.');return r.json()}).then(setSource).catch(e=>{if(e.name!=='AbortError')setLoadError('Mã mẫu chưa tải được. Bạn vẫn có thể tải project ZIP bên dưới.')})
    return()=>controller.abort()
  },[])
  const raw=source?.[format]||''
  let excerpt=raw
  if(format==='html') { const anchor=raw.indexOf('id="node-atlas-heading"');const start=anchor>=0?raw.lastIndexOf('<',anchor):-1;excerpt=start>=0?raw.slice(start).split('\n').slice(0,18).join('\n'):raw.split('\n').slice(12,32).join('\n') }
  else if(format==='css') {const start=raw.indexOf('.node-atlas-heading');excerpt=start>=0?raw.slice(start).split('\n').slice(0,24).join('\n'):raw.split('\n').slice(0,24).join('\n')}
  else {const anchor=raw.indexOf('id="node-atlas-heading"');const start=anchor>=0?raw.lastIndexOf('\n',anchor)+1:0;excerpt=raw.slice(start).split('\n').slice(0,2).join('\n')}
  if(format!=='css')excerpt=excerpt.replace(/ (className|class|id|data-figma-type|data-figma-name)=/g,'\n  $1=').replace(/>\{/g,'>\n  {').replace(/<\/p>/g,'\n</p>')
  const formats=[{id:'react',label:'React'},{id:'html',label:'HTML'},{id:'css',label:'CSS'}]
  const change=item=>{setFormat(item.id);setCopied(false);setNotice('')}
  async function copy(){try{await navigator.clipboard.writeText(excerpt);setCopied(true);setNotice('Đã sao chép đoạn mã.')}catch{setNotice('Không thể sao chép tự động. Bạn có thể chọn mã hoặc tải ZIP.')}}
  return <section className="lm-demo ly-container" id="demo" aria-labelledby="lm-demo-title">
    <div className="lm-heading lm-heading-centered"><span className="lm-kicker">THIẾT KẾ CỦA BẠN. MÃ NGUỒN CỦA BẠN.</span><h2 id="lm-demo-title">Từ một frame.<br/>Đến một khởi đầu mới.</h2><p>Xem thiết kế bên trái. Khám phá mã nguồn bên phải.<br className="lm-desktop-break"/> Đây là một mẫu được chuyển đổi bằng chính Layoutly.</p></div>
    <div className="lm-demo-stage">
      <div className="lm-design-panel"><div className="lm-demo-label"><Figma size={17}/><span>Thiết kế nguồn</span><small>Fixture Figma JSON / Atlas</small></div><div className="lm-design-canvas"><img src="/landing/showcase-atlas.png" alt="Thiết kế Atlas được render từ fixture gốc trong Layoutly" loading="lazy" width="1440" height="960"/><span className="lm-frame-size">1440 × 960</span></div></div>
      <div className="lm-conversion-arrow" aria-hidden="true"><ArrowRight size={23}/></div>
      <div className="lm-code-panel"><div className="lm-code-toolbar"><div role="tablist" aria-label="Định dạng mã mẫu">{formats.map((item,i)=><button key={item.id} id={'lm-code-'+item.id} role="tab" aria-selected={format===item.id} aria-controls="lm-code-output" tabIndex={format===item.id?0:-1} onClick={()=>change(item)} onKeyDown={e=>tabKeys(e,i,formats,change,'lm-code-')}>{item.label}</button>)}</div><button className="lm-copy" type="button" disabled={!raw} aria-label={copied?'Đã sao chép mã mẫu':'Sao chép mã mẫu'} onClick={copy}>{copied?<Check size={16}/>:<Copy size={16}/>}</button></div>
        <div id="lm-code-output" role="tabpanel" aria-labelledby={'lm-code-'+format} tabIndex={0}><div className="lm-code-file"><span/><span>{format==='react'?'atlas.jsx':format==='html'?'atlas.html':'styles.css'}</span><small>Trích đoạn bản xuất</small></div><pre><CodeExcerpt text={excerpt||(loadError?'Không tải được mã mẫu.\nTải ZIP để khám phá bản xuất.':'Đang tải mã mẫu…')}/></pre></div>
        <div className="lm-code-foot"><span><CheckCheck size={14}/>Mã nguồn từ fixture thật</span><span>.{format==='react'?'jsx':format}</span></div>
      </div>
    </div>
    <div className="lm-demo-bottom"><p>Mẫu gốc tự biên soạn, dùng để khám phá quy trình.<span role="status">{loadError||notice}</span></p><div><a href="/landing/atlas-figma.json" download className="ly-text-link">Figma JSON <ArrowDown size={14}/></a><a href={`/landing/atlas-${format==='react'?'react':'html'}.zip`} download className="ly-button ly-button-primary"><Download size={16}/>Tải mã mẫu {format==='react'?'React':'HTML/CSS'}</a></div></div>
  </section>
}
export default function LandingSections() {
  const [example,setExample]=useState(examples[2])
  return <div className="ly-landing-content">
    <section className="lm-work ly-container" id="work" aria-labelledby="work-title">
      <div className="lm-work-surface"><div className="lm-work-top"><div className="lm-heading"><span className="lm-kicker">QUY TRÌNH ĐƠN GIẢN</span><h2 id="work-title">Từ thiết kế đến sản phẩm.<br/>Từng bước, thật rõ ràng.</h2><p>Bốn bước để đưa ý tưởng ra khỏi canvas<br className="lm-desktop-break"/> và vào không gian làm việc của bạn.</p><a className="ly-text-link" href="/#workspace">Bắt đầu trải nghiệm <ArrowUpRight size={17}/></a></div><Shot src="/landing/workspace.png" alt="Workspace Layoutly thật với preview Atlas, layers và mã React" className="lm-work-shot"/></div>
      <ol className="lm-steps">{steps.map(({icon:Icon,title,text},i)=><li key={title}><span className={`lm-step-icon lm-step-${i}`}><Icon size={24}/></span><div><span className="lm-step-number">0{i+1}</span><h3>{title}</h3><p>{text}</p></div>{i<3&&<ArrowRight className="lm-step-arrow" size={18} aria-hidden="true"/>}</li>)}</ol></div>
    </section>
    <section className="lm-features ly-container" id="grow" aria-labelledby="features-title">
      <div className="lm-heading lm-feature-intro"><span className="lm-kicker">KHÔNG CHỈ LÀ XUẤT CODE</span><h2 id="features-title">Ít thao tác lặp lại.<br/>Nhiều không gian để sáng tạo.</h2><p>Thiết kế, kiểm tra và tiếp tục phát triển.<br/>Mọi thứ bạn cần, trong một luồng làm việc.</p></div>
      <article className="lm-feature-row"><div className="lm-feature-media lm-media-workspace"><Shot src="/landing/workspace.png" alt="Ảnh chụp Workspace Layoutly: cây layer, live preview và mã nguồn React"/><span className="lm-image-note"><Layers3 size={16}/>Layers, preview & code — cùng một nơi</span></div><div className="lm-feature-copy"><span className="lm-section-index">01 / KHÁM PHÁ THIẾT KẾ</span><h3>Một Workspace.<br/>Từ layer đến dòng code.</h3><p>Nhập frame bằng Figma REST hoặc Plugin Bridge. Xem giao diện, chọn từng layer và đối chiếu với mã nguồn trước khi mang về dự án.</p><ul><li><Check size={16}/>Preview và mã nguồn đặt cạnh nhau</li><li><Check size={16}/>Chuyển giữa HTML, CSS, React và JSON</li><li><Check size={16}/>Assets được lưu cùng snapshot thiết kế</li></ul><a className="ly-text-link" href="/#workspace">Khám phá Workspace <ArrowUpRight size={17}/></a></div></article>
      <article className="lm-feature-row lm-feature-reverse"><div className="lm-feature-media lm-media-projects"><Shot src="/landing/dashboard.png" alt="Dashboard Layoutly thật với bốn dự án mẫu đã lưu trong tài khoản" label="Layoutly / Dự án của tôi"/><span className="lm-image-note"><FolderOpen size={16}/>Dự án của bạn, sẵn sàng để tiếp tục</span></div><div className="lm-feature-copy"><span className="lm-section-index">02 / TIẾP TỤC PHÁT TRIỂN</span><h3>Lưu hôm nay.<br/>Tiếp tục ngày mai.</h3><p>Ý tưởng tốt cần thời gian. Lưu dự án trong tài khoản, tìm lại thiết kế và mở đúng không gian làm việc để tiếp tục chỉnh sửa.</p><ul><li><Check size={16}/>Tìm kiếm, đổi tên và nhân bản dự án</li><li><Check size={16}/>Mở lại thiết kế và lịch sử xuất</li><li><Check size={16}/>Tải ZIP HTML/CSS hoặc React cùng assets</li></ul><a className="ly-text-link" href="/#/dashboard">Đến dự án của tôi <ArrowUpRight size={17}/></a></div></article>
    </section>
    <section className="lm-showcase" id="showcase" aria-labelledby="lm-showcase-title"><div className="ly-container lm-showcase-layout"><div className="lm-showcase-visual"><div className="lm-showcase-window" id="lm-showcase-panel" role="tabpanel" aria-labelledby={'lm-example-'+example.id}><div className="lm-shot-chrome" aria-hidden="true"><span><i/><i/><i/></span><small>{example.name}</small><Monitor size={12}/></div><img src={`/landing/showcase-${example.id}.png`} alt={`Bản preview Layoutly của ${example.name}`} loading="lazy" width="1440" height="960"/></div><span className="lm-showcase-stamp"><Braces size={18}/><span>Thiết kế → giao diện<small>Preview được tạo bởi Layoutly</small></span></span></div><div className="lm-showcase-copy lm-heading"><span className="lm-kicker">ỨNG DỤNG THỰC TẾ</span><h2 id="lm-showcase-title">Mỗi ý tưởng,<br/>một diện mạo.</h2><p>Từ trang giới thiệu đến không gian quản lý.<br/>Chọn một mẫu để xem Layoutly chuyển đổi.</p><div className="lm-example-tabs" role="tablist" aria-label="Loại website mẫu">{examples.map((item,i)=><button key={item.id} id={'lm-example-'+item.id} role="tab" aria-selected={example.id===item.id} aria-controls="lm-showcase-panel" tabIndex={example.id===item.id?0:-1} onClick={()=>setExample(item)} onKeyDown={e=>tabKeys(e,i,examples,setExample,'lm-example-')}>{item.label}</button>)}</div><div className="lm-example-description" aria-live="polite"><span>{example.tag}</span><h3>{example.name}</h3><p>{example.text}</p></div><a className="ly-text-link" href="/#workspace">Thử với thiết kế của bạn <ArrowUpRight size={17}/></a><small className="lm-sample-note">Mẫu giao diện gốc tự biên soạn, không phải dự án khách hàng.</small></div></div></section>
    <ConversionDemo/>
    <section className="lm-faq ly-container" id="faq" aria-labelledby="lm-faq-title"><div className="lm-heading"><span className="lm-kicker">BẠN CÓ THỂ ĐANG TỰ HỎI</span><h2 id="lm-faq-title">Một vài điều<br/>trước khi bắt đầu.</h2><p>Mọi thứ rõ ràng để bạn tập trung<br/>vào điều mình muốn tạo ra.</p><a className="ly-text-link" href="/#/terms"><ShieldCheck size={16}/>Điều khoản & dữ liệu <ArrowUpRight size={15}/></a></div><div className="lm-faq-list">{questions.map(([question,answer],i)=><details key={question} name="layoutly-faq" open={i===0}><summary>{question}<Plus size={19}/></summary><p>{answer}</p></details>)}</div></section>
    <section className="lm-final-cta ly-container" aria-labelledby="lm-cta-title"><div className="lm-cta-surface"><div className="lm-cta-symbol" aria-hidden="true"><Figma size={28}/><ChevronRight size={18}/><Code2 size={30}/></div><span className="lm-kicker">Ý TƯỞNG TIẾP THEO ĐANG CHỜ BẠN</span><h2 id="lm-cta-title">Thiết kế đã sẵn sàng.<br/>Bắt đầu tạo điều tiếp theo.</h2><p>Đưa frame đầu tiên vào Layoutly.<br/>Phần sáng tạo còn lại thuộc về bạn.</p><div><a className="ly-button ly-button-accent" href="/#/register">Tạo tài khoản miễn phí <ArrowRight size={17}/></a><a className="lm-cta-demo" href="/#demo">Khám phá mã mẫu <ArrowUpRight size={16}/></a></div><small>Figma REST & Plugin Bridge · HTML/CSS & React · Mã nguồn của bạn</small></div></section>
  </div>
}
