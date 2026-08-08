import Brand from './Brand.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 상단 바. (D38)
 *
 * <p>세 줄로 나눈다. 올리브영이 쓰는 구조다.
 *
 * <pre>
 *   1줄  ☰   로고(홈)   내 적립금   장바구니     — 어디서든 같다
 *   2줄  전체 상품 · 오늘의 특가 · 마이페이지    — 최상위 이동
 *   3줄  현재 분류 + 카테고리 칩                  — 상품 화면에서만
 * </pre>
 *
 * <p>카테고리 칩을 3줄로 내린 것이 핵심이다. 처음에는 최상위 탭과 같은 높이에 뒀는데,
 * <b>"오늘의 특가"를 보는 중에도 카테고리가 떠 있어</b> 무엇에 속한 메뉴인지 알 수 없었다.
 * 카테고리는 상품 목록에만 걸리는 것이므로 그 화면에서만 보여야 한다.
 *
 * <p>적립금을 상단에 고정한 이유: 결제할 때마다 값이 바뀌는데 확인하러
 * 마이페이지에 들어가야 하면 번거롭다. 커머스에서 잔액은 늘 보이는 정보다.
 *
 * <p>스크롤을 내리면 통째로 접힌다. 목록을 훑는 동안 화면 위쪽을 헤더가 차지할 이유가 없다.
 */
export default function Header({
  hidden, view, current, categories, cartCount, me, point,
  onHome, onNavigate, onSelectCategory, onOpenMenu, onOpenCart,
}) {
  return (
    <header className={`topbar ${hidden ? 'up' : ''}`}>
      <div className="topbar-row">
        <button className="icon-btn" onClick={onOpenMenu} aria-label="카테고리 메뉴">
          <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
            <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="1.8"
                  strokeLinecap="round" />
          </svg>
        </button>

        {/* 로고는 홈으로 가는 버튼이다. 어느 화면에 있든 여기를 누르면 처음으로 돌아온다. */}
        <button className="brand-btn" onClick={onHome} aria-label="홈으로">
          <Brand />
        </button>

        <div className="top-right">
          <div className="mini-wallet">
            <span className="who">{me.customerId}</span>
            <b>{won(point)}P</b>
          </div>

          <button className="icon-btn cart-btn" onClick={onOpenCart} aria-label="장바구니">
            <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
              <path d="M3 4h2l2 9h8l2-6H6" stroke="currentColor" strokeWidth="1.6"
                    fill="none" strokeLinecap="round" strokeLinejoin="round" />
              <circle cx="8" cy="16" r="1.4" fill="currentColor" />
              <circle cx="15" cy="16" r="1.4" fill="currentColor" />
            </svg>
            {cartCount > 0 && <span className="cart-badge">{cartCount}</span>}
          </button>
        </div>
      </div>

      {/* ── 최상위 이동 ── */}
      <nav className="main-nav">
        <button className={view === 'shop' ? 'on' : ''} onClick={() => onNavigate('shop')}>
          전체 상품
        </button>
        <button className={view === 'event' ? 'on' : ''} onClick={() => onNavigate('event')}>
          오늘의 특가
        </button>
        <button className={view === 'mypage' ? 'on' : ''} onClick={() => onNavigate('mypage')}>
          마이페이지
        </button>
      </nav>

      {/* ── 카테고리: 상품 화면에서만 ── */}
      {view === 'shop' && (
        <>
          <div className="topbar-title">
            {current.subcategory ?? current.category ?? '전체 상품'}
          </div>

          <nav className="chips">
            <button className={!current.category ? 'on' : ''} onClick={() => onSelectCategory({})}>
              전체
            </button>
            {categories.map((c) => (
              <button
                key={c.category}
                className={current.category === c.category && !current.subcategory ? 'on' : ''}
                onClick={() => onSelectCategory({ category: c.category })}
              >
                {c.category}
              </button>
            ))}
          </nav>

          {current.category && (
            <nav className="chips sub">
              {(categories.find((c) => c.category === current.category)?.subcategories ?? [])
                .map((sub) => (
                  <button
                    key={sub}
                    className={current.subcategory === sub ? 'on' : ''}
                    onClick={() => onSelectCategory({
                      category: current.category,
                      // 같은 것을 다시 누르면 해제한다. 뒤로 가기를 찾지 않아도 된다.
                      subcategory: current.subcategory === sub ? undefined : sub,
                    })}
                  >
                    {sub}
                  </button>
                ))}
            </nav>
          )}
        </>
      )}
    </header>
  )
}
