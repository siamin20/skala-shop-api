/**
 * 작은 확인 창. (D38)
 *
 * <p>되돌리기 쉬운 동작에도 확인을 두는 이유는 <b>피드백</b> 때문이다.
 * 장바구니에 담는 것은 위험한 동작이 아니지만, 눌렀을 때 아무 반응이 없으면
 * 담겼는지 알 수 없어 두 번 누르게 된다.
 *
 * <p>토스트만으로도 알릴 수는 있다. 다만 담기 전에 <b>무엇을 몇 개</b> 담는지
 * 다시 보여주면 카드에서 수량을 잘못 고른 것을 그 자리에서 알아챈다.
 */
export default function ConfirmDialog({ open, title, message, confirmLabel, onConfirm, onCancel }) {
  if (!open) return null

  return (
    <div className="backdrop center-modal" onClick={onCancel}>
      {/* 안쪽 클릭이 바깥으로 전달되면 창이 닫힌다. */}
      <div className="mini-dialog" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3>{title}</h3>
        {message && <p className="mini-msg">{message}</p>}
        <div className="mini-actions">
          <button className="line" onClick={onCancel}>취소</button>
          <button className="primary" onClick={onConfirm}>{confirmLabel ?? '확인'}</button>
        </div>
      </div>
    </div>
  )
}
