const won = (n) => n.toLocaleString('ko-KR')

/**
 * 결제 전 확인 창.
 *
 * <p>처음에는 참여 버튼을 누르면 곧바로 결제가 끝났다. 선착순은 서두르는 화면이라
 * 오히려 <b>확인 절차가 더 필요하다.</b> 급하게 누르다 실수로 결제되면
 * 취소·환급을 거쳐야 하고, 그 사이 수량은 이미 남에게 갔다.
 *
 * <p>실제 커머스도 마찬가지다. 아무리 급한 특가라도 결제 직전에 무엇을 얼마에 사는지
 * 한 번 보여준다.
 *
 * <p>잔액이 모자라면 버튼을 막고 부족한 금액을 알려준다. 눌러서 실패를 보게 하는 것보다
 * 누르기 전에 알려주는 편이 낫다. 서버도 같은 검사를 하지만(INSUFFICIENT_POINT),
 * 화면에서 걸러주면 헛된 요청이 줄고 사용자는 즉시 무엇을 해야 할지 안다.
 */
export default function ConfirmDialog({ open, title, productName, price, quantity, point, busy, onConfirm, onCancel }) {
  if (!open) return null

  const total = price * quantity
  const shortage = total - point

  return (
    <div className="backdrop" onClick={onCancel}>
      {/* 안쪽 클릭이 바깥으로 전달되면 창이 닫힌다. 결제 창이 실수로 닫히면 안 된다. */}
      <div className="dialog" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3>{title}</h3>

        <div className="dialog-row">
          <span className="muted">상품</span>
          <span>{productName}</span>
        </div>
        <div className="dialog-row">
          <span className="muted">수량</span>
          <span>{quantity}개</span>
        </div>
        <div className="dialog-row total">
          <span>결제 금액</span>
          <span>{won(total)}P</span>
        </div>

        <div className="dialog-row small">
          <span className="muted">결제 후 잔액</span>
          <span className={shortage > 0 ? 'shortage' : ''}>
            {shortage > 0 ? `${won(shortage)}P 부족` : `${won(point - total)}P`}
          </span>
        </div>

        <div className="dialog-actions">
          <button className="line" onClick={onCancel} disabled={busy}>취소</button>
          <button className="primary" onClick={onConfirm} disabled={busy || shortage > 0}>
            {shortage > 0 ? '적립금 부족' : busy ? '처리 중…' : `${won(total)}P 결제`}
          </button>
        </div>
      </div>
    </div>
  )
}
