import Brand from './Brand.jsx'

export default function PublicFooter() {
  return <footer className="ly-footer ly-site-footer" id="stay">
    <div className="ly-container ly-site-footer-content">
      <div className="ly-site-footer-brand"><Brand /><p>Từ ý tưởng thiết kế đến mã nguồn của bạn. Một không gian để bắt đầu điều tiếp theo.</p></div>
      <div className="ly-footer-column"><h2>Sản phẩm</h2><nav aria-label="Sản phẩm"><a href="/#work">Cách hoạt động</a><a href="/#grow">Tính năng</a><a href="/#showcase">Dự án mẫu</a><a href="/#demo">Demo & mã nguồn</a></nav></div>
      <div className="ly-footer-column"><h2>Không gian của bạn</h2><nav aria-label="Tài khoản và dự án"><a href="/#workspace">Workspace</a><a href="/#/dashboard">Dự án của tôi</a><a href="/#/login">Đăng nhập</a><a href="/#/register">Tạo tài khoản</a></nav></div>
      <div className="ly-footer-column"><h2>Tìm hiểu thêm</h2><nav aria-label="Hỗ trợ và dữ liệu"><a href="/#faq">Câu hỏi thường gặp</a><a href="/#/terms">Điều khoản & dữ liệu</a><a href="/landing/atlas-figma.json" download>Figma JSON mẫu</a><a href="/landing/atlas-html.zip" download>HTML/CSS mẫu</a></nav></div>
    </div>
    <div className="ly-container ly-site-footer-bottom"><span>© {new Date().getFullYear()} Layoutly. Your design, your code.</span><span>Được tạo cho những ý tưởng muốn đi xa hơn canvas.</span></div>
  </footer>
}
