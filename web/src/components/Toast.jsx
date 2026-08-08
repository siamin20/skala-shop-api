import { useEffect } from 'react'

/**
 * 화면 위에 잠깐 떴다 사라지는 알림.
 *
 * <p>처음에는 본문 위쪽에 인라인으로 붙였다. 그런데 사용자의 시선은 방금 누른 버튼에
 * 머물러 있는데 안내는 화면 맨 위에 뜨니 <b>아무도 읽지 않았다.</b>
 * 스크롤을 내린 상태면 아예 보이지도 않는다.
 *
 * <p>그래서 화면에 떠 있는 층으로 옮겼다. 스크롤과 무관하게 늘 같은 자리에 뜬다.
 *
 * <p>성공은 자동으로 사라지고 <b>실패는 남긴다.</b> 실패는 사용자가 무언가 해야 한다는
 * 뜻이라 읽기 전에 사라지면 안 된다. 성공은 읽지 못해도 손해가 없다.
 */
export default function Toast({ toast, onClose }) {
  const isError = toast?.type === 'error'

  useEffect(() => {
    if (!toast || isError) return
    const id = setTimeout(onClose, 2600)
    return () => clearTimeout(id)
  }, [toast, isError, onClose])

  if (!toast) return null

  return (
    <div className={`toast ${isError ? 'err' : 'ok'}`} role="status">
      <span className="toast-icon">{isError ? '!' : '✓'}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div>{toast.message}</div>
        {toast.detail && <div className="toast-detail">{toast.detail}</div>}
      </div>
      <button className="toast-x" onClick={onClose} aria-label="닫기">×</button>
    </div>
  )
}
