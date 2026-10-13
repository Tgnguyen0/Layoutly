import { ArrowRight, ArrowUpRight, Braces, Check, Download, FileCode2, Figma, FolderOpen, Layers3, Monitor, Network, ShieldCheck, Sparkles } from 'lucide-react'
import BackendStatus from '../components/public/BackendStatus.jsx'
import ProductShowcase from '../components/public/ProductShowcase.jsx'

const features = [
  { icon: Figma, title: 'Figma Import', text: 'URL, File Key hoặc Figma Bridge. Bắt đầu ngay từ thiết kế của bạn.', tag: 'REST & Bridge' },
  { icon: Monitor, title: 'Live Preview', text: 'Xem giao diện được render từ snapshot, cùng mã nguồn thực tế.', tag: 'Đã có' },
  { icon: Network, title: 'Multi-page Navigation', text: 'Xuất nhiều trang và các liên kết prototype được hỗ trợ.', tag: 'Đã có' },
  { icon: FileCode2, title: 'HTML/CSS & React', text: 'Tải project ZIP với mã nguồn và assets để tiếp tục phát triển.', tag: 'Đã có' },
  { icon: Sparkles, title: 'AI Editor', text: 'Chỉnh sửa bằng hội thoại và duyệt từng thay đổi trước khi áp dụng.', tag: 'Chưa khả dụng', planned: true },
  { icon: FolderOpen, title: 'Saved Projects', text: 'Quản lý dự án theo tài khoản và mở lại lịch sử chỉnh sửa.', tag: 'Chưa khả dụng', planned: true },
]

export default function LandingPage() {
  return <main id="public-main">
    <section className="ly-hero">
      <div className="ly-container ly-hero-inner">
        <div className="ly-hero-copy">
          <span className="ly-eyebrow">CHUYỂN THIẾT KẾ FIGMA THÀNH CODE</span>
          <h1>Tạo code từ<br />Figma. <span>Nhanh hơn,</span><br />dễ hơn.</h1>
          <p>Layoutly giúp bạn chuyển thiết kế Figma thành HTML/CSS hoặc React. AI và quản lý dự án theo tài khoản sẽ có trong giai đoạn tiếp theo.</p>
          <div className="ly-hero-actions">
            <a className="ly-button ly-button-primary" href="/#workspace">Bắt đầu miễn phí <ArrowRight size={18} /></a>
            <a className="ly-button ly-button-quiet" href="#work">Xem demo</a>
          </div>
          <BackendStatus />
          <ul className="ly-hero-notes"><li><Check size={15} />Nhập thiết kế từ Figma hoặc plugin</li><li><Check size={15} />Chỉnh sửa bằng AI <small>Sắp có</small></li><li><Check size={15} />Xuất HTML/CSS hoặc React</li><li><Check size={15} />Lưu dự án theo tài khoản <small>Sắp có</small></li></ul>
        </div>
        <ProductShowcase />
      </div>
    </section>

    <section className="ly-work-band" id="work" aria-labelledby="work-title">
      <div className="ly-container">
        <div className="ly-section-heading"><div><span className="ly-eyebrow">TỪ THIẾT KẾ ĐẾN SẢN PHẨM</span><h2 id="work-title">Một workflow, mã nguồn của riêng bạn.</h2></div><a href="/#workspace" className="ly-text-link">Bắt đầu chuyển đổi <ArrowUpRight size={17} /></a></div>
        <div className="ly-workflow">
          <div><span className="ly-workflow-number">01</span><Figma size={25} /><h3>Thiết kế</h3><p>Figma REST hoặc Plugin Bridge</p></div>
          <ArrowRight className="ly-workflow-arrow" size={20} />
          <div><span className="ly-workflow-number">02</span><Layers3 size={25} /><h3>Snapshot</h3><p>DesignNode, layout và assets</p></div>
          <ArrowRight className="ly-workflow-arrow" size={20} />
          <div><span className="ly-workflow-number">03</span><Braces size={25} /><h3>Preview & code</h3><p>HTML, CSS, React và JSON</p></div>
          <ArrowRight className="ly-workflow-arrow" size={20} />
          <div><span className="ly-workflow-number">04</span><Download size={25} /><h3>Export</h3><p>Project ZIP cùng assets cục bộ</p></div>
        </div>
      </div>
    </section>

    <section className="ly-feature-section" id="grow" aria-labelledby="features-title">
      <div className="ly-container">
        <div className="ly-section-heading"><div><span className="ly-eyebrow">KHÔNG DỪNG LẠI Ở MỘT BẢN XEM TRƯỚC</span><h2 id="features-title">Không gian cho thiết kế tiếp tục phát triển.</h2></div></div>
        <div className="ly-feature-grid">{features.map(({ icon: Icon, title, text, tag, planned }) => <article key={title} className="ly-feature-item">
          <div className="ly-feature-top"><Icon size={23} /><span className={`ly-tag ${planned ? 'ly-tag-planned' : ''}`}>{tag}</span></div><h3>{title}</h3><p>{text}</p>
        </article>)}</div>
      </div>
    </section>

    <section className="ly-bridge-band" aria-labelledby="bridge-title"><div className="ly-container ly-bridge-inner">
      <div className="ly-bridge-symbol"><Figma size={38} /><span /><Braces size={38} /></div>
      <div><span className="ly-eyebrow">FIGMA BRIDGE</span><h2 id="bridge-title">Thiết kế vẫn ở trong tay bạn.</h2><p>Đồng bộ frame từ plugin vào workspace cục bộ. Không cần gửi Figma PAT cho dữ liệu plugin được phép truy cập.</p></div>
      <a href="/#workspace" className="ly-button ly-button-primary">Mở Figma Bridge <ArrowRight size={17} /></a>
    </div></section>
    <div className="ly-container ly-privacy-note"><ShieldCheck size={18} /><p>AI và lưu dự án theo tài khoản chưa khả dụng trong phiên bản này. Workspace chuyển đổi hiện tại vẫn sử dụng được.</p></div>
  </main>
}
