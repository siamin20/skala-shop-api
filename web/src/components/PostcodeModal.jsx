import { useEffect, useRef, useState } from 'react'

const SCRIPT_SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

/**
 * 우편번호 검색 창. (D34, D41)
 *
 * <h2>왜 별도의 창인가</h2>
 *
 * <p>주문서 안에 그대로 끼워 넣으면 검색 화면이 열릴 때 아래 내용이 통째로 밀린다.
 * 배송지를 채우다 말고 화면이 움직이면 어디를 보고 있었는지 잃어버린다.
 * 창으로 띄우면 뒤 화면이 그 자리에 있고, 닫으면 하던 자리로 돌아온다.
 *
 * <h2>브라우저 팝업은 쓰지 않는다</h2>
 *
 * <p>다음 우편번호 서비스는 {@code open()}으로 새 브라우저 창을 띄우는 방식도 제공한다.
 * 그건 쓰지 않는다. <b>차단 설정에 걸리면 아무 일도 일어나지 않고</b>, 사용자는
 * 버튼이 고장 난 것으로 본다. 실제로 개발 중에 그렇게 막혔다.
 *
 * <p>대신 이 컴포넌트가 화면 위에 겹치는 창을 직접 그리고, 그 안쪽에
 * {@code embed()}로 검색 화면을 넣는다. 사용자에게는 창으로 보이면서
 * 차단 설정과는 무관하다.
 *
 * <h2>스크립트를 미리 받지 않는다</h2>
 *
 * <p>외부 스크립트라 첫 화면 로딩에 얹히면 그만큼 느려진다.
 * 이 창이 열릴 때 처음 받고, 그다음부터는 이미 받아둔 것을 쓴다.
 */
export default function PostcodeModal({ open, onSelect, onClose }) {
  const box = useRef(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!open) {
      setFailed(false)
      return
    }

    // 창이 닫힌 뒤 늦게 도착한 콜백이 상태를 건드리지 않게 막는다.
    let alive = true

    const draw = () => {
      // 상태가 반영되어 그릴 자리가 생긴 뒤에 호출해야 한다.
      // 같은 틱에 부르면 ref가 아직 비어 있다.
      requestAnimationFrame(() => {
        if (!alive || !box.current) return
        try {
          new window.daum.Postcode({
            oncomplete: (data) => {
              if (!alive) return
              onSelect({
                zipcode: data.zonecode,
                // 도로명 주소가 없는 지역이 있다. 그때는 지번으로 채운다.
                address: data.roadAddress || data.jibunAddress,
              })
            },
            onclose: (state) => {
              // 사용자가 검색 화면 자체를 닫은 경우에만 창을 닫는다.
              // 주소를 고른 경우에도 onclose가 오는데 그건 위에서 이미 처리했다.
              if (alive && state === 'FORCE_CLOSE') onClose()
            },
            width: '100%',
            height: '100%',
          }).embed(box.current)
        } catch {
          if (alive) setFailed(true)
        }
      })
    }

    if (window.daum?.Postcode) {
      draw()
    } else {
      const script = document.createElement('script')
      script.src = SCRIPT_SRC
      script.onload = () => { if (alive) draw() }
      // 사내망이나 오프라인이면 받아오지 못한다. 그때는 직접 입력으로 안내한다.
      // 검색이 안 되는 것과 주문을 못 하는 것은 다른 문제다.
      script.onerror = () => { if (alive) setFailed(true) }
      document.body.appendChild(script)
    }

    return () => { alive = false }
  }, [open, onSelect, onClose])

  // ESC로 닫는다. 창을 띄웠으면 키보드로도 빠져나갈 수 있어야 한다.
  useEffect(() => {
    if (!open) return
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="backdrop" onClick={onClose}>
      <div
        className="postcode-modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="우편번호 검색"
      >
        <div className="sheet-head">
          <h3>우편번호 검색</h3>
          <button className="sheet-x" onClick={onClose} aria-label="닫기">×</button>
        </div>

        {failed ? (
          <div className="empty">
            주소 검색을 불러오지 못했습니다.<br />
            창을 닫고 주소를 직접 입력해 주세요.
          </div>
        ) : (
          <div ref={box} className="postcode-frame" />
        )}
      </div>
    </div>
  )
}
