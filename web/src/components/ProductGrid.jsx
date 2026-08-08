import { useState } from 'react'
import ProductArt from './ProductArt.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 상품 카드 하나.
 *
 * 수량 상태를 카드마다 따로 갖는다. 목록 전체가 하나의 수량을 공유하면
 * 한 상품에서 3을 입력하고 다른 상품을 사려는 순간 3이 따라간다.
 */
function ProductCard({ product, onOrder, busy }) {
  const [quantity, setQuantity] = useState(1)
  const soldOut = product.stock <= 0
  const low = product.stock > 0 && product.stock <= 10

  return (
    <div className="card">
      <div className="thumb">
        <ProductArt name={product.name} />
        {soldOut && <span className="tag grey">SOLD OUT</span>}
        {low && <span className="tag">품절임박</span>}
      </div>

      <p className="pname">{product.name}</p>
      <div className="price">{won(product.price)}<small>P</small></div>
      <div className={`stock ${low ? 'low' : ''}`}>
        {soldOut ? '재고 없음' : `재고 ${won(product.stock)}개`}
      </div>

      <div className="buy">
        <input
          type="number"
          min="1"
          value={quantity}
          disabled={soldOut}
          onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
          aria-label={`${product.name} 수량`}
        />
        <button
          className="primary tiny"
          style={{ flex: 1 }}
          disabled={soldOut || busy}
          onClick={() => onOrder(product, quantity)}
        >
          {soldOut ? '품절' : '담기'}
        </button>
      </div>
    </div>
  )
}

export default function ProductGrid({ products, onOrder, busy }) {
  if (!products.length) return <div className="empty">상품이 없습니다.</div>

  return (
    <div className="grid">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} onOrder={onOrder} busy={busy} />
      ))}
    </div>
  )
}
