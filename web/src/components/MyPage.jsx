const won = (n) => n.toLocaleString('ko-KR')

/**
 * 마이페이지. 적립금·배송지·주문내역을 한곳에서 본다. (D38)
 *
 * <p>주문 내역을 상단 탭에서 여기로 옮겼다. 상단은 <b>상품을 찾는 동선</b>이고
 * 주문 내역은 <b>산 뒤에 보는 것</b>이라 성격이 다르다.
 * 실제 커머스가 마이페이지로 모으는 이유이기도 하다.
 */
export default function MyPage({ me, orders, address, busy, onCancel }) {
  const products = orders?.products ?? []

  return (
    <div className="mypage">
      <section className="mp-card">
        <div className="mp-user">
          <b>{me.customerId}</b> 님
        </div>
        <div className="mp-point">
          <span className="muted small">보유 적립금</span>
          <div className="point-big">{won(orders?.point ?? 0)}<small>P</small></div>
        </div>
      </section>

      <section className="mp-block">
        <h3>배송지</h3>
        {address ? (
          <div className="mp-address">
            <b>{address.recipient}</b>
            <span className="muted">{address.phone}</span>
            <p>{address.fullAddress}</p>
          </div>
        ) : (
          <p className="muted small">
            등록된 배송지가 없습니다. 주문서에서 입력하면 자동으로 저장됩니다.
          </p>
        )}
      </section>

      <section className="mp-block">
        <h3>주문 · 배송 내역</h3>

        {products.length === 0 ? (
          <div className="empty">주문한 상품이 없습니다</div>
        ) : (
          <>
            {products.map((item) => (
              <div key={item.productId} className="order-card">
                <div className="order-status">배송 준비중</div>

                <div className="order-main">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p className="order-name">{item.productName}</p>
                    <p className="muted small">
                      {won(item.price)}원 · {item.quantity}개
                    </p>
                  </div>
                  <b>{won(item.totalPrice)}원</b>
                </div>

                <div className="order-actions">
                  {/* 수량을 지정해 일부만 취소한다. 환급액은 서버가 실제 결제액으로 계산한다. */}
                  <CancelControl
                    max={item.quantity}
                    busy={busy}
                    onCancel={(q) => onCancel(item.productId, q)}
                  />
                </div>
              </div>
            ))}

            <div className="mp-total">
              <span>총 결제 금액</span>
              <b>{won(orders.totalSpent)}원</b>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

/**
 * 취소 수량 선택.
 *
 * <p>전량 취소 버튼을 따로 둔다. 대부분은 전부 취소하는데 매번 숫자를 맞추게 하면
 * 번거롭다. 부분 취소는 수량을 직접 정한다.
 */
function CancelControl({ max, busy, onCancel }) {
  return (
    <div className="cancel-row">
      <button className="line tiny" disabled={busy} onClick={() => onCancel(max)}>
        전체 취소
      </button>
      {max > 1 && (
        <button className="text-link" disabled={busy} onClick={() => {
          const input = prompt(`취소할 수량 (1~${max})`, '1')
          const q = Number(input)
          if (q >= 1 && q <= max) onCancel(q)
        }}>
          부분 취소
        </button>
      )}
    </div>
  )
}
