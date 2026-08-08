import { useState } from 'react'
import ProductArt from './ProductArt.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 상품 카드 하나.
 *
 * 수량 상태를 카드마다 따로 갖는다. 목록 전체가 하나의 수량을 공유하면
 * 한 상품에서 3을 입력하고 다른 상품을 사려는 순간 3이 따라간다.
 */
function ProductCard({ product, onAdd, onBuyNow, busy }) {
  const [quantity, setQuantity] = useState(1)
  const soldOut = product.stock <= 0
  // 정확한 재고 수량은 보여주지 않는다. 실제 커머스도 그렇다.
  // 남은 수량을 그대로 노출하면 매출과 재고 상황이 경쟁사에 그대로 드러나고,
  // 사용자에게도 "3개 남음"과 "300개 남음"은 구매 결정에 차이를 만들지 않는다.
  // 정말 임박했을 때만 알려주는 편이 신호가 된다.
  const low = product.stock > 0 && product.stock <= 10

  return (
    <div className="card">
      <div className="thumb">
        <ProductArt name={product.name} />
        {soldOut && <span className="tag grey">SOLD OUT</span>}
        {low && <span className="tag">품절임박</span>}
      </div>

      <p className="pcat">{product.subcategory}</p>
      <p className="pname">{product.name}</p>
      <div className="price">{won(product.price)}<small>원</small></div>
      {low && <div className="stock low">품절 임박</div>}

      <div className="buy">
        <input
          type="number"
          min="1"
          value={quantity}
          disabled={soldOut}
          onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
          aria-label={`${product.name} 수량`}
        />
        <button className="line tiny" disabled={soldOut || busy}
                onClick={() => onAdd(product, quantity)}>
          담기
        </button>
        <button className="primary tiny" disabled={soldOut || busy}
                onClick={() => onBuyNow(product, quantity)}>
          {soldOut ? '품절' : '구매'}
        </button>
      </div>
    </div>
  )
}

export default function ProductGrid({ products, onAdd, onBuyNow, busy }) {
  if (!products.length) return <div className="empty">해당 분류에 상품이 없습니다</div>

  return (
    <div className="grid">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} onAdd={onAdd} onBuyNow={onBuyNow} busy={busy} />
      ))}
    </div>
  )
}
