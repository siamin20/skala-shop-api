import { useEffect, useState } from 'react'

/**
 * 수량을 입력받는 작은 창. 부분 취소에 쓴다. (D48)
 *
 * <h2>왜 브라우저 prompt를 쓰지 않는가</h2>
 *
 * <p>처음에는 {@code window.prompt()}로 만들었다. 동작은 정확했지만 네 가지가 걸린다.
 *
 * <ul>
 *   <li><b>화면과 따로 논다.</b> 앱의 다른 확인 절차는 전부 자체 창을 쓰는데 여기만 브라우저 기본 창이다
 *   <li><b>차단될 수 있다.</b> 일부 브라우저·확장·샌드박스에서 prompt가 막힌다.
 *       막히면 버튼을 눌러도 아무 일이 없다. 주소 검색 팝업에서 이미 같은 문제를 겪었다 (D41)
 *   <li><b>왜 안 되는지 알려주지 못한다.</b> 범위를 벗어난 값을 넣으면 조용히 아무 일도 안 일어난다
 *   <li><b>무엇을 무르는지 안 보인다.</b> 상품명도 현재 수량도 창에 담을 수 없다
 * </ul>
 *
 * <h2>잘못된 값을 어떻게 다루는가</h2>
 *
 * <p>입력을 막지 않고 <b>이유를 보여주고 확인 버튼을 잠근다.</b>
 * 숫자만 받도록 입력을 가로채면 사용자는 자기가 뭘 잘못 눌렀는지 모른 채
 * 글자가 안 써지는 경험을 한다.
 */
export default function QuantityDialog({ open, title, itemName, max, busy, onConfirm, onCancel }) {
  const [value, setValue] = useState('1')

  // 창을 다시 열 때 지난 입력이 남아 있으면 안 된다.
  useEffect(() => {
    if (open) setValue('1')
  }, [open])

  // 창을 띄웠으면 키보드로도 빠져나갈 수 있어야 한다.
  useEffect(() => {
    if (!open) return
    const onKey = (e) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null

  const quantity = Number(value)
  const invalid = !/^\d+$/.test(value.trim())
    ? '숫자만 입력해 주세요'
    : quantity < 1 ? '1개 이상이어야 합니다'
    : quantity > max ? `주문 수량은 ${max}개입니다`
    : null

  const submit = () => { if (!invalid && !busy) onConfirm(quantity) }

  return (
    <div className="backdrop center-modal" onClick={onCancel}>
      <div
        className="mini-dialog"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <h3>{title}</h3>
        {itemName && <p className="mini-msg">{itemName} · 주문 {max}개</p>}

        <div className="qty-field">
          <input
            type="text"
            inputMode="numeric"
            value={value}
            autoFocus
            aria-label="취소할 수량"
            className={invalid ? 'invalid' : ''}
            onChange={(e) => setValue(e.target.value)}
            // 엔터로도 확인된다. 작은 창에서 마우스로 옮겨 가게 하지 않는다.
            onKeyDown={(e) => { if (e.key === 'Enter') submit() }}
          />
          <span className="muted">개</span>
        </div>
        {invalid && <p className="field-err">{invalid}</p>}

        <div className="mini-actions">
          <button className="line" onClick={onCancel} disabled={busy}>닫기</button>
          <button className="primary" onClick={submit} disabled={!!invalid || busy}>
            {busy ? '처리 중…' : '취소하기'}
          </button>
        </div>
      </div>
    </div>
  )
}
