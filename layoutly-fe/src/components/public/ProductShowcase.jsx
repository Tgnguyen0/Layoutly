import { ArrowRight, Figma, Menu, ShoppingBag } from 'lucide-react'

function SneakerSite({ mobile = false }) {
  return <div className={`ly-sneaker-site ${mobile ? 'ly-sneaker-mobile' : ''}`}>
    <div className="ly-sneaker-nav"><strong>Sneaker.</strong>{mobile ? <Menu size={13} /> : <span>Home&nbsp;&nbsp;&nbsp; Products&nbsp;&nbsp;&nbsp; About <ShoppingBag size={12} /></span>}</div>
    <div className="ly-sneaker-hero"><div><small>NEW COLLECTION</small><strong>Find your<br />perfect style</strong><p>Comfort in every step.</p><span className="ly-shop-label">Shop now <ArrowRight size={10} /></span></div><img src="/brand/sneaker.png" alt="Giày sneaker da màu trắng ngà" fetchPriority="high" /></div>
    <div className="ly-sneaker-stats"><span><b>500+</b>Brands</span><span><b>10K+</b>Happy customers</span><span><b>4.8</b>Average rating</span></div>
  </div>
}

export default function ProductShowcase({ compact = false }) {
  return <div className={`ly-product-scene ${compact ? 'ly-product-compact' : ''}`} role="img" aria-label="Minh họa website sneaker trên trình duyệt, điện thoại và mã React">
    <div className="ly-browser-mock"><div className="ly-browser-chrome"><i /><i /><i /><span>preview.layoutly</span></div><SneakerSite /></div>
    <div className="ly-code-mock"><div><i /><i /><i /><span>Hero.jsx</span></div><pre><code><em>export default function</em> <b>Hero</b>() {'{'}{'\n'}  <em>return</em> ({'\n'}    <span>&lt;section</span> className=<b>"hero"</b><span>&gt;</span>{'\n'}      <span>&lt;h1&gt;</span>Find your perfect style<span>&lt;/h1&gt;</span>{'\n'}      <span>&lt;button&gt;</span>Shop now<span>&lt;/button&gt;</span>{'\n'}    <span>&lt;/section&gt;</span>{'\n'}  ){ '\n'}{'}'}</code></pre></div>
    <div className="ly-phone-mock"><div className="ly-phone-top"><span>9:41</span><i /></div><SneakerSite mobile /></div>
    <div className="ly-figma-badge"><Figma size={36} aria-hidden="true" /><span>Figma</span></div>
  </div>
}
