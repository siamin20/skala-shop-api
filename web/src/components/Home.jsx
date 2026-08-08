import { useEffect, useState } from 'react'
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
  const live = sales.filter((s) => s.remaining > 0)

  /**
   * 배너에 특가를 돌아가며 보여준다. (D40)
   *
   * <p>특가가 여러 개인데 하나만 띄우면 나머지는 아무도 보지 못한다.
   * 배너 자리는 하나뿐이므로 시간으로 나눠 쓴다.
   *
   * <p>4초로 뒀다. 더 짧으면 읽기 전에 넘어가고, 더 길면 두 번째 특가를 보기 전에
   * 사용자가 스크롤을 내려버린다.
   */
  const [slot, setSlot] = useState(0)

  useEffect(() => {
    if (live.length <= 1) return
    const id = setInterval(() => setSlot((n) => (n + 1) % live.length), 4000)
    // 특가 목록이 바뀌면 타이머를 다시 건다. 정리하지 않으면 타이머가 쌓인다.
    return () => clearInterval(id)
  }, [live.length])

  const liveSale = live[slot % Math.max(live.length, 1)]

  return (
    <div className="home">
      {/* ── 배너 ── */}
      <section className="banner" onClick={onGoEvent} role="button" tabIndex={0}
               onKeyDown={(e) => e.key === 'Enter' && onGoEvent()}>
        <div className="banner-copy">
          <span className="banner-tag">TODAY ONLY</span>

          {liveSale ? (
            <>
              {/* key를 바꿔 다시 마운트시킨다. 그래야 넘어갈 때마다 등장 효과가 다시 돈다. */}
              <div key={liveSale.id} className="banner-swap">
                <h2>
                  <em>{liveSale.discountRate}%</em> {liveSale.name}
                </h2>
                <p>
                  <s>{won(liveSale.listPrice)}원</s>
                  <b>{won(liveSale.price)}원</b>
                  <span className="banner-left">{liveSale.remaining}개 남음</span>
                </p>
              </div>

              {live.length > 1 && (
                <div className="banner-dots" aria-hidden="true">
                  {live.map((s, i) => <i key={s.id} className={i === slot ? 'on' : ''} />)}
                </div>
              )}
            </>
          ) : (
            <>
              <h2>오늘의 특가</h2>
              <p>준비 중인 특가가 있어요</p>
            </>
          )}

          <span className="banner-cta">지금 보러 가기 →</span>
        </div>
        <div className="banner-art">
          {liveSale && <ProductArt key={liveSale.id} name={liveSale.productName} size="100%" />}
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
                  <div className="sale-mini-price">
                    {s.discountRate > 0 && <em>{s.discountRate}%</em>}
                    {won(s.price)}원
                  </div>
                  {s.discountRate > 0 && <s className="sale-mini-list">{won(s.listPrice)}원</s>}
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
