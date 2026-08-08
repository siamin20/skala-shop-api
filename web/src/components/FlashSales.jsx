import { useEffect, useState } from 'react'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 남은 시간을 초 단위로 흘려보낸다.
 *
 * 선착순의 핵심은 급박함이다. 정지된 숫자로는 전달되지 않는다.
 * 1초마다 다시 그리되 타이머는 하나만 둔다. 이벤트마다 setInterval을 걸면
 * 목록이 길어질수록 타이머가 그만큼 늘어난다.
 */
function useTicker() {
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(id)   // 언마운트 때 정리하지 않으면 타이머가 남는다
  }, [])
}

function remainingText(endsAt) {
  const ms = new Date(endsAt) - Date.now()
  if (ms <= 0) return null

  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60

  if (d > 0) return `${d}일 ${h}시간 남음`
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')} 남음`
}

/**
 * 선착순 이벤트 목록.
 *
 * 이 화면이 프로젝트의 차별화를 눈으로 보여주는 자리다.
 * 서버에서는 네 가지 방식으로 수량을 지키는데(D23), 사용자에게는
 * "정확히 N개만 팔린다"는 결과로만 드러난다. 게이지가 그 결과를 보여준다.
 */
export default function FlashSales({ sales, onJoin, busy, onRefresh }) {
  useTicker()

  if (!sales.length) return <div className="empty">진행 중인 이벤트가 없습니다.</div>

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        <button className="line tiny" onClick={onRefresh}>새로고침</button>
      </div>

      {sales.map((sale) => {
        const left = remainingText(sale.endsAt)
        const notStarted = new Date(sale.startsAt) > Date.now()
        const closed = !left || notStarted
        const soldOut = sale.remaining <= 0
        // 판매율. 게이지는 "얼마나 팔렸는가"를 보여준다.
        const soldRate = Math.round((sale.sold / sale.totalQuantity) * 100)

        return (
          <div key={sale.id} className={`event ${closed || soldOut ? 'closed' : ''}`}>
            <div className="event-top">
              <div style={{ minWidth: 0 }}>
                <h3>{sale.name}</h3>
                <div className="muted small">
                  {sale.productName} · {won(sale.price)}P
                </div>
              </div>

              {closed ? (
                <span className="badge muted small">
                  {notStarted ? '오픈 예정' : '종료'}
                </span>
              ) : (
                <span className="live"><i />LIVE · {left}</span>
              )}
            </div>

            <div className="gauge">
              <i style={{ width: `${soldRate}%` }} />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="remain">
                {soldOut
                  ? '준비된 수량이 모두 소진되었습니다'
                  : <>남은 수량 <b>{sale.remaining}</b> / {sale.totalQuantity}개 ({soldRate}% 판매)</>}
              </span>

              <button
                className="primary tiny"
                disabled={closed || soldOut || busy}
                onClick={() => onJoin(sale)}
              >
                {soldOut ? '품절' : closed ? '참여 불가' : '지금 참여'}
              </button>
            </div>
          </div>
        )
      })}
    </>
  )
}
