const won = (n) => n.toLocaleString('ko-KR')

/**
 * 장바구니. (D37)
 *
 * <p>담은 것을 확인하고 수량을 고친 뒤 한 번에 결제한다.
 * 상품마다 따로 결제하면 카드 승인이 상품 수만큼 일어나고, 중간에 하나가 거절되면
 * 앞의 것만 결제된 어중간한 상태가 남는다.
 */
export default function CartSheet({ open, items, total, onClose, onQuantity, onRemove, onCheckout }) {
  if (!open) return null

  return (
    <div className="drawer-backdrop right" onClick={onClose}>
      <aside className="drawer right" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <h3>장바구니 {items.length > 0 && <span className="muted">{items.length}</span>}</h3>
          <button className="sheet-x" onClick={onClose} aria-label="닫기">×</button>
        </div>

        {items.length === 0 ? (
          <div className="empty">담은 상품이 없습니다</div>
        ) : (
          <>
            <div className="drawer-body">
              {items.map((item) => (
                <div key={item.productId} className="cart-line">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p className="cart-name">{item.name}</p>
                    <p className="cart-price">{won(item.price * item.quantity)}원</p>
                  </div>

                  <div className="qty">
                    <button onClick={() => onQuantity(item.productId, item.quantity - 1)}
                            disabled={item.quantity <= 1}>−</button>
                    <span>{item.quantity}</span>
                    <button onClick={() => onQuantity(item.productId, item.quantity + 1)}>+</button>
                  </div>

                  <button className="text-link" onClick={() => onRemove(item.productId)}>
                    삭제
                  </button>
                </div>
              ))}
            </div>

            <div className="drawer-foot pay">
              <div className="sum-row total">
                <span>총 상품 금액</span><span>{won(total)}원</span>
              </div>
              <button className="primary" style={{ width: '100%' }} onClick={onCheckout}>
                주문서 작성
              </button>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
