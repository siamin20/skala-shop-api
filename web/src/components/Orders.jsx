import { useState } from 'react'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 주문 내역과 부분 취소.
 *
 * 취소는 수량을 지정한다. 전량 취소로 수량이 0이 되면 항목이 사라진다.
 * 환급액은 서버가 계산한다. 화면에서 단가 × 수량으로 계산하면
 * 가격이 바뀐 뒤 재주문한 항목에서 실제 결제액과 어긋난다. (D15)
 */
export default function Orders({ orders, onCancel, busy }) {
  const [quantities, setQuantities] = useState({})

  if (!orders || !orders.products?.length) {
    return <div className="empty">주문한 상품이 없습니다.</div>
  }

  const setQuantity = (productId, value) =>
    setQuantities((prev) => ({ ...prev, [productId]: Math.max(1, Number(value) || 1) }))

  return (
    <>
      <table>
        <thead>
          <tr>
            <th>상품</th>
            <th className="num">단가</th>
            <th className="num">수량</th>
            <th className="num">결제액</th>
            <th className="num">취소</th>
          </tr>
        </thead>
        <tbody>
          {orders.products.map((item) => {
            const quantity = quantities[item.productId] ?? 1
            return (
              <tr key={item.productId}>
                <td>{item.productName}</td>
                <td className="num muted">{won(item.price)}P</td>
                <td className="num">{item.quantity}</td>
                <td className="num"><b>{won(item.totalPrice)}P</b></td>
                <td className="num">
                  <div style={{ display: 'inline-flex', gap: 6 }}>
                    <input
                      type="number"
                      min="1"
                      max={item.quantity}
                      value={quantity}
                      style={{ width: 56, textAlign: 'center', padding: '5px 4px' }}
                      onChange={(e) => setQuantity(item.productId, e.target.value)}
                      aria-label={`${item.productName} 취소 수량`}
                    />
                    <button
                      className="line tiny"
                      disabled={busy}
                      onClick={() => onCancel(item.productId, Math.min(quantity, item.quantity))}
                    >
                      취소
                    </button>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      <p className="muted small" style={{ marginTop: 14 }}>
        총 결제액 <b>{won(orders.totalSpent)}P</b> · 잔여 포인트 <b>{won(orders.point)}P</b>
      </p>
    </>
  )
}
