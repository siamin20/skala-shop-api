import { useCallback, useEffect, useMemo, useState } from 'react'
import PostcodeModal from './PostcodeModal.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/** 비어 있는 배송지 입력 폼. */
const EMPTY = {
  label: '', recipient: '', phone: '', zipcode: '',
  address: '', addressDetail: '', entrancePassword: '',
}

/**
 * 주문서 페이지. (D31, D32, D34, D40, D41)
 *
 * <h2>왜 창이 아니라 페이지인가</h2>
 *
 * <p>처음에는 모달 창으로 만들었다가 페이지로 바꿨다. 주문서에 들어가는 것이
 * 생각보다 많다 — 주문 상품, 배송지 선택, 배송지 추가 폼, 결제 수단, 카드 입력,
 * 금액 요약. 이걸 창 안에 넣으면 <b>창 안에서 또 스크롤을 하게 된다.</b>
 * 안쪽 스크롤과 바깥 스크롤이 겹치면 어디를 굴리고 있는지 알기 어렵다.
 *
 * <p>페이지로 두면 화면 전체를 쓰고, 브라우저 뒤로 가기가 자연스럽게 동작하며,
 * 결제 단계에 들어왔다는 것이 사용자에게 분명하게 보인다. 실제 커머스도 이렇게 한다.
 *
 * <h2>순서</h2>
 *
 * <p>배송지 → 결제 수단 → 금액 순으로 둔다. 어디로 보낼지 정하고, 어떻게 낼지 정하고,
 * 마지막에 얼마인지 확인한다. 금액을 맨 위에 두면 아직 배송비가 정해지기 전이라
 * 숫자가 도중에 바뀐다.
 */
export default function CheckoutPage({
  items, point, addresses, busy, onBack, onSubmit, onAddAddress, onError,
}) {
  /** 고른 배송지의 id. null이면 새로 입력하는 중이다. */
  const [selectedId, setSelectedId] = useState(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [postcodeOpen, setPostcodeOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  const [method, setMethod] = useState('CARD')
  const [usePoint, setUsePoint] = useState(0)
  const [card, setCard] = useState({ cardNumber: '', expiry: '', cvc: '' })

  // 저장된 배송지가 있으면 기본 배송지를 골라둔다. 목록의 첫 항목이 기본이다.
  // 없으면 바로 입력 폼을 연다. 빈 목록만 보여주면 다음에 뭘 해야 할지 알 수 없다.
  useEffect(() => {
    if (!addresses) return
    if (addresses.length) {
      setSelectedId((prev) => (prev ?? addresses[0].id))
      setAdding(false)
    } else {
      setAdding(true)
      setSelectedId(null)
    }
  }, [addresses])

  const selected = useMemo(
    () => addresses?.find((a) => a.id === selectedId) ?? null,
    [addresses, selectedId],
  )

  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0)

  // 포인트로 낼 수 있는 상한. 보유 잔액과 결제 총액 중 작은 쪽이다.
  // 넘겨서 쓰면 거스름돈이 포인트로 생긴다.
  const maxPoint = Math.min(point, total)
  const applied = method === 'CARD' ? Math.min(usePoint, maxPoint) : total
  const cardAmount = total - applied
  const earned = method === 'CARD' ? Math.floor((cardAmount * 5) / 100) : 0

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const fillFromSearch = useCallback(({ zipcode, address }) => {
    setForm((f) => ({ ...f, zipcode, address }))
    setPostcodeOpen(false)
  }, [])

  // ── 입력 검증 ──
  //
  // 서버도 같은 것을 검증한다(DeliveryAddressRequest). 여기서 한 번 더 보는 이유는
  // 오타 하나 때문에 왕복 한 번을 기다리게 하지 않기 위해서다. 서버 검증을 대체하지 않는다.
  const formReady = form.recipient.trim()
    && /^01[016789]-?\d{3,4}-?\d{4}$/.test(form.phone.replace(/\s/g, ''))
    && /^\d{5}$/.test(form.zipcode)
    && form.address.trim()

  const addressReady = adding ? formReady : !!selected

  const cardReady = method !== 'CARD' || cardAmount === 0
    || (card.cardNumber.replace(/\D/g, '').length >= 13
      && /^\d{2}\/\d{2}$/.test(card.expiry)
      && card.cvc.length >= 3)

  const pointEnough = method !== 'POINT' || point >= total

  /** 입력한 배송지를 저장하고 그것을 선택 상태로 만든다. */
  async function saveNewAddress() {
    setSaving(true)
    try {
      const created = await onAddAddress({
        ...form,
        // 이름을 안 적으면 목록에서 구분이 안 된다. 주소 앞부분으로 대신 붙인다.
        label: form.label.trim() || form.address.split(' ').slice(0, 2).join(' '),
        isDefault: !addresses?.length,
      })
      setSelectedId(created.id)
      setAdding(false)
      setForm(EMPTY)
    } catch (e) {
      onError(e)
    } finally {
      setSaving(false)
    }
  }

  /**
   * 결제 요청에 실을 배송지. (D42)
   *
   * <p>저장된 것을 골랐으면 <b>id만</b> 보낸다. 내용을 다시 보내면 안 된다.
   * 목록 응답에는 공동현관 비밀번호가 들어 있지 않아서(서버가 내려주지 않는다),
   * 그대로 되보내면 <b>저장돼 있던 비밀번호가 빈 값으로 덮인다.</b>
   *
   * <p>새로 입력한 경우에만 내용을 보낸다. 그때는 서버가 저장하고 이 주문에 쓴다.
   */
  function deliveryPayload() {
    if (!adding && selected) {
      return { deliveryAddressId: selected.id, delivery: null }
    }
    return {
      deliveryAddressId: null,
      delivery: {
        label: form.label.trim() || null,
        recipient: form.recipient,
        phone: form.phone,
        zipcode: form.zipcode,
        address: form.address,
        addressDetail: form.addressDetail,
        entrancePassword: form.entrancePassword,
      },
    }
  }

  return (
    <div className="checkout-page">
      <div className="page-head">
        <button className="back" onClick={onBack} aria-label="뒤로">←</button>
        <h2>주문서</h2>
      </div>

      <div className="checkout-grid">
        <div className="checkout-main">

          {/* ── 주문 상품 ── */}
          <section className="card">
            <h3>주문 상품 <span className="muted small">{items.length}건</span></h3>
            {items.map((i) => (
              <div key={i.productId} className="order-line">
                <span style={{ flex: 1, minWidth: 0 }}>{i.name}</span>
                <span className="muted">{i.quantity}개</span>
                <b>{won(i.price * i.quantity)}원</b>
              </div>
            ))}
          </section>

          {/* ── 배송지 ── */}
          <section className="card">
            <div className="card-head">
              <h3>배송지</h3>
              {!!addresses?.length && (
                <button
                  className="text-link"
                  onClick={() => { setAdding(!adding); setForm(EMPTY) }}
                >
                  {adding ? '저장된 배송지에서 고르기' : '새 배송지 입력'}
                </button>
              )}
            </div>

            {/* 저장해 둔 배송지 목록. 라디오로 하나만 고른다. */}
            {!adding && (
              <div className="addr-list">
                {addresses?.map((a) => (
                  <label
                    key={a.id}
                    className={`addr-item ${a.id === selectedId ? 'on' : ''}`}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={a.id === selectedId}
                      onChange={() => setSelectedId(a.id)}
                    />
                    <div className="addr-body">
                      <div className="addr-top">
                        <b>{a.label || a.recipient}</b>
                        {a.isDefault && <span className="badge-default">기본</span>}
                      </div>
                      <p className="muted small">{a.recipient} · {a.phone}</p>
                      <p className="small">
                        ({a.zipcode}) {a.address} {a.addressDetail}
                      </p>
                      {/* 서버는 공동현관 비밀번호 값을 내려주지 않는다. 등록 여부만 알려준다.
                          목록 응답에 실어 보내면 화면을 보는 것만으로 남의 현관이 열린다. */}
                      {a.hasEntrancePassword && (
                        <p className="muted small">공동현관 비밀번호 등록됨</p>
                      )}
                    </div>
                  </label>
                ))}
              </div>
            )}

            {/* 새 배송지 입력 */}
            {adding && (
              <div className="form-grid">
                <input placeholder="배송지 이름 (집, 회사 등)" value={form.label}
                       onChange={set('label')} />
                <input placeholder="받는 분" value={form.recipient}
                       onChange={set('recipient')} />
                <input placeholder="연락처 (01012345678)" value={form.phone}
                       onChange={set('phone')} inputMode="numeric" />

                <div className="zip-row">
                  <input placeholder="우편번호" value={form.zipcode} readOnly />
                  <button type="button" className="line tiny"
                          onClick={() => setPostcodeOpen(true)}>
                    주소 검색
                  </button>
                </div>

                <input placeholder="주소" value={form.address} readOnly />
                <input placeholder="상세 주소 (동·호수)" value={form.addressDetail}
                       onChange={set('addressDetail')} />

                {/*
                  공동현관 비밀번호.
                  배송 실패의 흔한 원인이라 배송지에 함께 저장한다.
                  다만 단독주택이나 현관이 열린 건물도 있어 필수로 두지 않는다.
                */}
                <input placeholder="공동현관 비밀번호 (선택)" value={form.entrancePassword}
                       onChange={set('entrancePassword')} />

                <button
                  className="line"
                  disabled={!formReady || saving}
                  onClick={saveNewAddress}
                >
                  {saving ? '저장 중…' : '이 배송지 저장하고 사용하기'}
                </button>
              </div>
            )}
          </section>

          {/* ── 결제 수단 ── */}
          <section className="card">
            <h3>결제 수단</h3>
            <div className="pay-tabs">
              <button className={method === 'CARD' ? 'on' : ''}
                      onClick={() => setMethod('CARD')}>
                신용·체크카드
              </button>
              <button className={method === 'POINT' ? 'on' : ''}
                      onClick={() => setMethod('POINT')}>
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
                  <p className="muted small">
                    보유 {won(point)}P · 최대 {won(maxPoint)}P 사용 가능
                  </p>
                </div>

                {cardAmount > 0 && (
                  <div className="form-grid" style={{ marginTop: 12 }}>
                    <input
                      placeholder="카드번호"
                      value={card.cardNumber}
                      inputMode="numeric"
                      onChange={(e) => setCard({
                        ...card, cardNumber: e.target.value.replace(/\D/g, ''),
                      })}
                    />
                    <div className="zip-row">
                      <input placeholder="MM/YY" value={card.expiry}
                             onChange={(e) => setCard({ ...card, expiry: e.target.value })} />
                      <input placeholder="CVC" value={card.cvc} inputMode="numeric"
                             onChange={(e) => setCard({
                               ...card, cvc: e.target.value.replace(/\D/g, ''),
                             })} />
                    </div>
                  </div>
                )}
              </>
            )}

            {method === 'POINT' && !pointEnough && (
              <p className="field-err">적립금이 {won(total - point)}P 부족합니다</p>
            )}
          </section>
        </div>

        {/* ── 금액 요약 ── 넓은 화면에서는 옆에 붙어 따라온다. */}
        <aside className="checkout-side">
          <section className="card summary">
            <h3>결제 금액</h3>
            <div className="sum-row"><span>상품 금액</span><span>{won(total)}원</span></div>
            {applied > 0 && (
              <div className="sum-row">
                <span>적립금 사용</span><span className="minus">-{won(applied)}P</span>
              </div>
            )}
            <div className="sum-row total">
              <span>최종 결제 금액</span><span>{won(cardAmount)}원</span>
            </div>
            {earned > 0 && (
              <div className="sum-row earn"><span>적립 예정</span><span>+{won(earned)}P</span></div>
            )}

            <button
              className="primary"
              disabled={busy || !addressReady || !cardReady || !pointEnough}
              onClick={() => onSubmit({
                ...deliveryPayload(),
                paymentMethod: method,
                usePoint: applied,
                card: method === 'CARD' && cardAmount > 0 ? card : null,
              })}
            >
              {busy ? '결제 중…'
                : !addressReady ? '배송지를 선택해 주세요'
                : `${won(cardAmount)}원 결제하기`}
            </button>
          </section>
        </aside>
      </div>

      <PostcodeModal
        open={postcodeOpen}
        onSelect={fillFromSearch}
        onClose={() => setPostcodeOpen(false)}
      />
    </div>
  )
}
