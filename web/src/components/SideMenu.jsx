import Brand from './Brand.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 옆에서 밀려 나오는 메뉴. (D38)
 *
 * <p>세 덩어리로 나눈다. <b>내 정보 → 바로 갈 곳 → 카테고리</b> 순이다.
 * 실제 커머스가 쓰는 순서로, 자주 쓰는 것이 손가락 가까운 위쪽에 온다.
 *
 * <p>사용자 영역을 카드로 띄웠다. 아이디만 한 줄로 두면 배경과 구분되지 않아
 * <b>지금 누구로 로그인했는지 눈에 들어오지 않는다.</b> 공용 기기에서 특히 문제가 된다.
 *
 * <p>로그아웃은 맨 아래 작은 글씨다. 자주 쓰지 않는데 위에 두면 자리를 차지하고
 * 무엇보다 실수로 누르기 쉽다.
 */
export default function SideMenu({
  open, categories, current, me, point, cartCount,
  onSelect, onClose, onLogout, onNavigate, onOpenCart,
}) {
  if (!open) return null

  const go = (view) => () => { onNavigate(view); onClose() }

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <Brand />
          <button className="sheet-x" onClick={onClose} aria-label="닫기">×</button>
        </div>

        {/* ── 내 정보 ── */}
        <div className="drawer-profile">
          <div className="avatar">{me.customerId.slice(0, 2).toUpperCase()}</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="profile-id">{me.customerId} <span className="muted">님</span></div>
            <div className="profile-point">{won(point)}<small>P</small></div>
          </div>
        </div>

        {/* ── 바로 갈 곳 ── */}
        <div className="drawer-quick">
          <button onClick={go('mypage')}>
            <span className="q-icon">👤</span>마이페이지
          </button>
          <button onClick={() => { onOpenCart(); onClose() }}>
            <span className="q-icon">🛒</span>장바구니
            {cartCount > 0 && <em>{cartCount}</em>}
          </button>
          <button onClick={go('event')}>
            <span className="q-icon">🔥</span>오늘의 특가
          </button>
          <button onClick={go('mypage')}>
            <span className="q-icon">📦</span>주문·배송
          </button>
        </div>

        {/* ── 카테고리 ── */}
        <div className="drawer-body">
          <p className="drawer-section">카테고리</p>

          <button
            className={`drawer-cat ${!current.category ? 'on' : ''}`}
            onClick={() => { onSelect({}); onClose() }}
          >
            전체 상품
          </button>

          {categories.map((c) => (
            <div key={c.category} className="drawer-group">
              <button
                className={`drawer-cat ${current.category === c.category ? 'on' : ''}`}
                onClick={() => { onSelect({ category: c.category }); onClose() }}
              >
                {c.category}
              </button>
              <div className="drawer-subs">
                {c.subcategories.map((sub) => (
                  <button
                    key={sub}
                    className={current.subcategory === sub ? 'on' : ''}
                    onClick={() => {
                      onSelect({ category: c.category, subcategory: sub })
                      onClose()
                    }}
                  >
                    {sub}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="drawer-foot">
          <button className="text-link" onClick={onLogout}>로그아웃</button>
        </div>
      </aside>
    </div>
  )
}
