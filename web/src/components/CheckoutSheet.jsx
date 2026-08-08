import { useEffect, useRef, useState } from 'react'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 주문서. 배송지 → 결제수단 → 결제. (D31, D32, D34)
 *
 * <p>실제 커머스의 주문서 순서를 따랐다. 어디로 보낼지 정하고, 어떻게 낼지 정하고,
 * 마지막에 얼마인지 확인한다. 금액을 먼저 보여주면 배송비가 정해지기 전이라 숫자가 바뀐다.
 *
 * <h2>주소 검색</h2>
 *
 * <p>다음 우편번호 서비스를 쓴다. 우편번호와 도로명 주소를 사용자가 직접 치게 하면
 * <b>오타가 나고 그대로 배송 사고가 된다.</b> 검색으로 채우고 동·호수만 직접 받는다.
 *
 * <p>스크립트를 미리 불러오지 않고 <b>버튼을 누를 때</b> 넣는다. 주문서를 열 때마다
 * 외부 스크립트를 받으면 첫 화면이 그만큼 늦어진다.
 */
export default function CheckoutSheet({ open, items, point, address, busy, onClose, onSubmit }) {
  const [form, setForm] = useState({
    recipient: '', phone: '', zipcode: '', address: '', addressDetail: '',
  })
  const [method, setMethod] = useState('CARD')
  const [usePoint, setUsePoint] = useState(0)
  const [card, setCard] = useState({ cardNumber: '', expiry: '', cvc: '' })

  /**
   * 주소 검색을 쓸 수 없을 때 직접 입력으로 전환한다.
   *
   * <p>외부 스크립트라 사내망이나 오프라인에서는 받아오지 못한다.
   * 그때 칸이 읽기 전용으로 잠겨 있으면 <b>주문 자체를 할 수 없다.</b>
   * 검색이 안 되는 것과 주문을 못 하는 것은 다른 문제다.
   */
  const [manualAddress, setManualAddress] = useState(false)

  /** 주소 검색을 끼워 넣을 자리. 팝업 대신 이 안에 그린다. */
  const [searching, setSearching] = useState(false)
  const postcodeBox = useRef(null)

  // 저장된 배송지가 있으면 채워 넣는다. 매번 다시 치게 하지 않는다.
  useEffect(() => {
    if (address) {
      setForm({
        recipient: address.recipient ?? '',
        phone: address.phone ?? '',
        zipcode: address.zipcode ?? '',
        address: address.address ?? '',
        addressDetail: address.addressDetail ?? '',
      })
    }
  }, [address, open])

  if (!open || !items?.length) return null

  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0)
  // 포인트로 낼 수 있는 상한. 보유 잔액과 결제 총액 중 작은 쪽이다.
  // 넘기면 거스름돈이 포인트로 생긴다.
  const maxPoint = Math.min(point, total)
  const applied = method === 'CARD' ? Math.min(usePoint, maxPoint) : total
  const cardAmount = total - applied
  const earned = method === 'CARD' ? Math.floor((cardAmount * 5) / 100) : 0

  const addressReady = form.recipient && form.phone && form.zipcode && form.address
  const cardReady = method !== 'CARD' || cardAmount === 0
    || (card.cardNumber.replace(/\D/g, '').length >= 13 && /^\d{2}\/\d{2}$/.test(card.expiry) && card.cvc.length >= 3)
  const pointEnough = method !== 'POINT' || point >= total

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  /**
   * 다음 우편번호 창을 띄운다.
   *
   * <p>스크립트를 미리 불러오지 않고 <b>버튼을 누를 때</b> 넣는다. 주문서를 열 때마다
   * 외부 스크립트를 받으면 첫 화면이 그만큼 늦어진다.
   *
   * <p>프로토콜을 생략한 {@code //} 형태를 쓰지 않는다. 일부 환경에서 http로 내려받으려다
   * 혼합 콘텐츠로 차단된다. https로 못 박는다.
   *
   * <p>팝업({@code open})이 아니라 <b>주문서 안에 끼워 넣는다</b>({@code embed}).
   * 팝업은 브라우저나 확장 프로그램이 막으면 아무 일도 일어나지 않고,
   * 사용자는 버튼이 고장 난 것으로 본다. 실제로 이 환경에서 그렇게 막혔다.
   * 끼워 넣으면 차단 설정과 무관하게 동작하고 화면 이동도 없다.
   */
  function searchAddress() {
    const openPostcode = () => {
      setSearching(true)

      // 상태가 반영되어 자리가 생긴 뒤에 그려야 한다.
      // 같은 틱에 부르면 ref가 아직 null이다.
      setTimeout(() => {
        if (!postcodeBox.current) {
          setManualAddress(true)
          setSearching(false)
          return
        }
        try {
          new window.daum.Postcode({
            oncomplete: (data) => {
              setForm((f) => ({
                ...f,
                zipcode: data.zonecode,
                // 도로명이 없는 지역이 있어 지번으로 넘어간다.
                address: data.roadAddress || data.jibunAddress,
              }))
              setSearching(false)
            },
            onclose: () => setSearching(false),
            width: '100%',
            height: '100%',
          }).embed(postcodeBox.current)
        } catch {
          setManualAddress(true)
          setSearching(false)
        }
      }, 0)
    }

    if (window.daum?.Postcode) {
      openPostcode()
      return
    }

    const script = document.createElement('script')
    script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
    script.onload = openPostcode
    // 받아오지 못하면 직접 입력으로 연다. 검색이 안 된다고 주문을 막을 이유가 없다.
    script.onerror = () => setManualAddress(true)
    document.body.appendChild(script)
  }

  return (
    <div className="backdrop" onClick={onClose}>
      <div className="sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="sheet-head">
          <h3>주문서</h3>
          <button className="sheet-x" onClick={onClose} aria-label="닫기">×</button>
        </div>

        <div className="sheet-body">
          {/* ── 주문 상품 ── */}
          <section>
            <h4>주문 상품</h4>
            {items.map((i) => (
              <div key={i.productId} className="order-line">
                <span style={{ flex: 1, minWidth: 0 }}>{i.name}</span>
                <span className="muted">{i.quantity}개</span>
                <b>{won(i.price * i.quantity)}원</b>
              </div>
            ))}
          </section>

          {/* ── 배송지 ── */}
          <section>
            <h4>배송지</h4>
            <div className="form-grid">
              <input placeholder="받는 분" value={form.recipient} onChange={set('recipient')} />
              <input placeholder="연락처 (01012345678)" value={form.phone} onChange={set('phone')} />
              <div className="zip-row">
                <input
                  placeholder="우편번호"
                  value={form.zipcode}
                  readOnly={!manualAddress}
                  onChange={set('zipcode')}
                />
                <button type="button" className="line tiny" onClick={searchAddress}>
                  주소 검색
                </button>
              </div>
              <input
                placeholder="주소"
                value={form.address}
                readOnly={!manualAddress}
                onChange={set('address')}
              />
              {searching && (
                <div className="postcode-box">
                  <div ref={postcodeBox} className="postcode-frame" />
                  <button type="button" className="line tiny" onClick={() => setSearching(false)}>
                    검색 닫기
                  </button>
                </div>
              )}
              {manualAddress && (
                <p className="muted small">
                  주소 검색을 불러오지 못했습니다. 직접 입력해 주세요.
                </p>
              )}
              <input placeholder="상세 주소 (동·호수)" value={form.addressDetail}
                     onChange={set('addressDetail')} />
            </div>
          </section>

          {/* ── 결제 수단 ── */}
          <section>
            <h4>결제 수단</h4>
            <div className="pay-tabs">
              <button className={method === 'CARD' ? 'on' : ''} onClick={() => setMethod('CARD')}>
                신용·체크카드
              </button>
              <button className={method === 'POINT' ? 'on' : ''} onClick={() => setMethod('POINT')}>
                적립금 전액
              </button>
            </div>

            {method === 'CARD' && (
              <>
                <div className="point-use">
                  <label>적립금 사용</label>
                  <div className="zip-row">
                    <input
                      type="number" min="0" max={maxPoint} value={usePoint}
                      onChange={(e) => setUsePoint(Math.max(0, Number(e.target.value) || 0))}
                    />
                    <button className="line tiny" onClick={() => setUsePoint(maxPoint)}>
                      전액 사용
                    </button>
                  </div>
                  <p className="muted small">보유 {won(point)}P · 최대 {won(maxPoint)}P 사용 가능</p>
                </div>

                {cardAmount > 0 && (
                  <div className="form-grid" style={{ marginTop: 12 }}>
                    <input
                      placeholder="카드번호"
                      value={card.cardNumber}
                      onChange={(e) => setCard({ ...card, cardNumber: e.target.value.replace(/\D/g, '') })}
                      inputMode="numeric"
                    />
                    <div className="zip-row">
                      <input placeholder="MM/YY" value={card.expiry}
                             onChange={(e) => setCard({ ...card, expiry: e.target.value })} />
                      <input placeholder="CVC" value={card.cvc} inputMode="numeric"
                             onChange={(e) => setCard({ ...card, cvc: e.target.value.replace(/\D/g, '') })} />
                    </div>
                  </div>
                )}
              </>
            )}

            {method === 'POINT' && !pointEnough && (
              <p className="field-err">적립금이 {won(total - point)}P 부족합니다</p>
            )}
          </section>

          {/* ── 결제 금액 ── */}
          <section className="summary">
            <div className="sum-row"><span>상품 금액</span><span>{won(total)}원</span></div>
            {applied > 0 && (
              <div className="sum-row"><span>적립금 사용</span>
                <span className="minus">-{won(applied)}P</span></div>
            )}
            <div className="sum-row total">
              <span>최종 결제 금액</span><span>{won(cardAmount)}원</span>
            </div>
            {earned > 0 && (
              <div className="sum-row earn"><span>적립 예정</span><span>+{won(earned)}P</span></div>
            )}
          </section>
        </div>

        <div className="sheet-foot">
          <button
            className="primary"
            disabled={busy || !addressReady || !cardReady || !pointEnough}
            onClick={() => onSubmit({
              delivery: form,
              paymentMethod: method,
              usePoint: applied,
              card: method === 'CARD' && cardAmount > 0 ? card : null,
            })}
          >
            {busy ? '결제 중…'
              : !addressReady ? '배송지를 입력해 주세요'
              : `${won(cardAmount)}원 결제하기`}
          </button>
        </div>
      </div>
    </div>
  )
}
