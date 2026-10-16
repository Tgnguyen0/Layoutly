import { ArrowRight, ArrowUpRight, Check } from 'lucide-react'
import BackendStatus from '../components/public/BackendStatus.jsx'
import ProductShowcase from '../components/public/ProductShowcase.jsx'
import LandingSections from '../components/public/LandingSections.jsx'

export default function LandingPage() {
  return <main id="public-main">
    <section className="ly-hero">
      <div className="ly-container ly-hero-inner">
        <div className="ly-hero-copy">
          <span className="ly-eyebrow">CHUYỂN THIẾT KẾ FIGMA THÀNH CODE</span>
          <h1>Tạo code từ<br />Figma. <span>Nhanh hơn,</span><br />dễ hơn.</h1>
          <p>Từ bản thiết kế đến giao diện có thể chạy. Nhập Figma, xem trước kết quả và mang mã HTML/CSS hoặc React về dự án của bạn.</p>
          <div className="ly-hero-actions">
            <a className="ly-button ly-button-primary" href="/#workspace">Bắt đầu miễn phí <ArrowRight size={18} /></a>
            <a className="ly-button ly-button-quiet" href="#work">Cách hoạt động <ArrowUpRight size={16} /></a>
          </div>
          <BackendStatus />
          <ul className="ly-hero-notes"><li><Check size={15} />Kết nối Figma hoặc dùng plugin Bridge</li><li><Check size={15} />Xem giao diện, kiểm tra mã nguồn</li><li><Check size={15} />Xuất HTML/CSS hoặc React</li></ul>
        </div>
        <ProductShowcase />
      </div>
    </section>
    <LandingSections />
  </main>
}
