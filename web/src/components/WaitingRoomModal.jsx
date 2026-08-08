/**
 * 대기열 화면.
 *
 * <p>서버는 몰릴 때 거절하는 대신 줄을 세운다(D30). 그 상태를 사용자에게 보여주는 자리다.
 *
 * <p><b>기다리는 화면에서 가장 중요한 것은 "멈춰 있지 않다"는 신호다.</b>
 * 순번 숫자만 있으면 화면이 정지한 것처럼 보이고, 사람은 그때 창을 닫는다.
 * 그래서 아래에 색이 계속 흐르는 띠를 뒀다. 진행률이 아니라 <b>살아 있다는 표시</b>다.
 *
 * <p>진행률 막대를 쓰지 않은 이유가 있다. 대기열은 앞사람이 언제 빠질지 모르므로
 * 몇 퍼센트인지 정직하게 계산할 수 없다. 가짜 진행률을 보여주면 90%에서 멈춰
 * 오히려 더 답답해진다.
 */
export default function WaitingRoomModal({ ticket, saleName, onCancel }) {
  if (!ticket || ticket.admitted) return null

  const seconds = ticket.estimatedSeconds ?? 0
  const wait = seconds >= 60
    ? `약 ${Math.ceil(seconds / 60)}분`
    : `약 ${Math.max(seconds, 1)}초`

  return (
    <div className="backdrop">
      <div className="dialog queue" role="dialog" aria-modal="true" aria-live="polite">
        <div className="muted small">{saleName}</div>

        <div className="num">{ticket.position.toLocaleString('ko-KR')}</div>
        <div className="of">번째로 대기 중이에요</div>

        {/* 색이 흐르는 띠. 진행률이 아니라 "처리가 계속되고 있다"는 신호다. */}
        <div className="queue-flow" aria-hidden="true"><i /></div>

        <div className="queue-meta">
          <span>내 앞에 <b>{Math.max(ticket.position - 1, 0).toLocaleString('ko-KR')}명</b></span>
          <span className="muted">예상 {wait}</span>
        </div>

        <p className="muted small" style={{ marginTop: 14, lineHeight: 1.6 }}>
          순서가 되면 자동으로 넘어갑니다.<br />
          창을 닫으면 대기 순번이 사라집니다.
        </p>

        <button className="line" style={{ width: '100%', marginTop: 14 }} onClick={onCancel}>
          대기 취소
        </button>
      </div>
    </div>
  )
}
