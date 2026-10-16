import { ArrowRight, Figma, Menu, ShoppingBag } from 'lucide-react'

function SneakerSite({ mobile = false }) {
  return <div className={`ly-sneaker-site ${mobile ? 'ly-sneaker-mobile' : ''}`}>
    <div className="ly-sneaker-nav"><strong>Sneaker.</strong>{mobile ? <Menu size={13} /> : <span>Home&nbsp;&nbsp;&nbsp; Products&nbsp;&nbsp;&nbsp; About <ShoppingBag size={12} /></span>}</div>
    <div className="ly-sneaker-hero"><div><small>NEW COLLECTION</small><strong>Find your<br />perfect style</strong><p>Comfort in every step.</p><span className="ly-shop-label">Shop now <ArrowRight size={10} /></span></div><img src="/brand/sneaker.png" alt="Giày sneaker da màu trắng ngà" fetchpriority="high" /></div>
    <div className="ly-sneaker-stats"><span><b>500+</b>Brands</span><span><b>10K+</b>Happy customers</span><span><b>4.8</b>Average rating</span></div>
  </div>
}

export default function ProductShowcase({ compact = false }) {
  return <div className={`ly-product-scene ${compact ? 'ly-product-compact' : ''}`} role="img" aria-label="Minh họa website sneaker trên trình duyệt, điện thoại và mã React">
    <div className="ly-browser-mock"><div className="ly-browser-chrome"><i /><i /><i /><span>preview.layoutly</span></div><SneakerSite /></div>
    <div className="ly-code-mock"><div><i /><i /><i /><span>Hero.jsx</span></div><pre><code><em>export default function</em> <b>Hero</b>() {'{'}{'\n'}  <em>return</em> ({'\n'}    <span>&lt;section</span> className=<b>"hero"</b><span>&gt;</span>{'\n'}      <span>&lt;h1&gt;</span>Find your perfect style<span>&lt;/h1&gt;</span>{'\n'}      <span>&lt;button&gt;</span>Shop now<span>&lt;/button&gt;</span>{'\n'}    <span>&lt;/section&gt;</span>{'\n'}  ){ '\n'}{'}'}</code></pre></div>
    <div className="ly-phone-mock">
      <div className="ly-phone-screen">
        <div className="ly-phone-top">
          <span>9:41</span>
          <div className="ly-phone-status-icons" aria-hidden="true">
            <svg width="10" height="7" viewBox="0 0 14 10" fill="currentColor"><path d="M1 8h2v2H1V8zm3-2h2v4H4V6zm3-2h2v6H7V4zm3-3h2v9h-2V1z"/></svg>
            <svg width="12" height="7" viewBox="0 0 16 8" fill="none" stroke="currentColor"><rect x="0.5" y="0.5" width="13" height="7" rx="2" strokeWidth="1"/><rect x="2" y="2" width="8" height="4" rx="1" fill="currentColor"/><path d="M15 2.5v3" strokeWidth="1" strokeLinecap="round"/></svg>
          </div>
        </div>
        <SneakerSite mobile />
        <div className="ly-phone-home-indicator" aria-hidden="true" />
      </div>
      <img src="/brand/phone-frame.png" alt="" className="ly-phone-frame" aria-hidden="true" />
    </div>
    <div className="ly-figma-badge"><Figma size={36} aria-hidden="true" /><span>Figma</span></div>
  </div>
}
