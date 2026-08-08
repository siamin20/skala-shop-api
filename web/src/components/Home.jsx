import ProductArt from './ProductArt.jsx'

const won = (n) => n.toLocaleString('ko-KR')

/**
 * 홈. (D40)
 *
 * <p>처음에는 홈이 곧 전체 상품 목록이었다. 그런데 커머스에서 홈은 <b>목록이 아니라
 * 편집된 화면</b>이다. 무엇을 봐야 할지 모르는 사용자에게 오늘의 특가와 인기 상품을
 * 먼저 보여주고, 카테고리로 들어갈 입구를 준다.
 *
 * <p>전체 목록을 홈에 두면 19개든 1,900개든 그냥 나열될 뿐이라 아무것도 안내하지 못한다.
 *
 * <h2>구성</h2>
 *
 * <ol>
 *   <li>배너 — 지금 가장 밀고 있는 것 하나
 *   <li>카테고리 입구 — 목적이 분명한 사용자가 바로 빠져나갈 길
 *   <li>오늘의 특가 — 시간이 걸린 것부터. 놓치면 못 산다
 *   <li>인기 상품 — 무엇을 살지 모르는 사용자를 위한 기본값
 * </ol>
 */
export default function Home({ sales, best, categories, onSelectCategory, onGoEvent, onProduct }) {
  const liveSale = sales.find((s) => s.remaining > 0)

  return (
    <div className="home">
      {/* ── 배너 ── */}
      <section className="banner" onClick={onGoEvent} role="button" tabIndex={0}
               onKeyDown={(e) => e.key === 'Enter' && onGoEvent()}>
        <div className="banner-copy">
          <span className="banner-tag">TODAY ONLY</span>
          <h2>오늘의 특가</h2>
          <p>{liveSale ? liveSale.name : '준비 중인 특가가 있어요'}</p>
          <span className="banner-cta">지금 보러 가기 →</span>
        </div>
        <div className="banner-art">
          {liveSale && <ProductArt name={liveSale.productName} size="100%" />}
        </div>
      </section>

      {/* ── 카테고리 입구 ── */}
      <section className="home-cats">
        {categories.map((c) => (
          <button key={c.category} onClick={() => onSelectCategory({ category: c.category })}>
            <span className="cat-dot" aria-hidden="true" />
            {c.category}
          </button>
        ))}
      </section>

      {/* ── 진행 중인 특가 ── */}
      {sales.length > 0 && (
        <section className="home-block">
          <div className="block-head">
            <h3>마감 임박 특가</h3>
            <button className="text-link" onClick={onGoEvent}>더보기</button>
          </div>

          <div className="sale-strip">
            {sales.slice(0, 3).map((s) => {
              const rate = Math.round((s.sold / s.totalQuantity) * 100)
              return (
                <div key={s.id} className="sale-mini">
                  <div className="sale-mini-art"><ProductArt name={s.productName} /></div>
                  <p className="sale-mini-name">{s.productName}</p>
                  <div className="sale-mini-price">{won(s.price)}원</div>
                  <div className="gauge"><i style={{ width: `${rate}%` }} /></div>
                  <span className="remain small">
                    {s.remaining > 0 ? <>{s.remaining}개 남음</> : '품절'}
                  </span>
                </div>
              )
            })}
          </div>
        </section>
      )}

      {/* ── 인기 상품 ── */}
      <section className="home-block">
        <div className="block-head">
          <h3>지금 인기 있는 상품</h3>
          <button className="text-link" onClick={() => onSelectCategory({})}>전체보기</button>
        </div>

        <div className="rank-list">
          {best.slice(0, 5).map((p, i) => (
            <button key={p.id} className="rank-row" onClick={() => onProduct(p)}>
              {/* 순위를 크게 둔다. 커머스에서 랭킹은 그 자체가 구매 근거다. */}
              <span className="rank-no">{i + 1}</span>
              <div className="rank-art"><ProductArt name={p.name} /></div>
              <div className="rank-info">
                <p className="rank-cat">{p.subcategory}</p>
                <p className="rank-name">{p.name}</p>
                <b>{won(p.price)}원</b>
              </div>
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}
