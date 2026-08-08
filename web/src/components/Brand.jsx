/**
 * SKALA BEAUTY 워드마크. (D44)
 *
 * <p>SK의 공식 로고를 가져다 쓰지 않았다. 실제 기업 상표를 복제하는 것은
 * 과제물이라 해도 적절하지 않아서, 이름만 빌려 새로 그렸다.
 *
 * <h2>왜 다시 그렸나</h2>
 *
 * <p>처음에는 둥근 사각형 안에 알파벳 S를 넣었다. 흔하고 뷰티와 아무 관련이 없었다.
 * 로고는 <b>무엇을 파는 곳인지</b>를 한눈에 말해야 한다.
 *
 * <p>꽃잎 네 장을 겹친 모양으로 바꿨다. 뷰티·코스메틱 브랜드가 즐겨 쓰는 형태이고,
 * 가운데가 비어 있어 작은 크기에서도 형태가 뭉개지지 않는다.
 */
export default function Brand({ size = 'md' }) {
  const big = size === 'lg'
  const box = big ? 40 : 28
  const title = big ? 28 : 18

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: big ? 11 : 8 }}>
      <svg width={box} height={box} viewBox="0 0 48 48" aria-hidden="true">
        <defs>
          <linearGradient id="skala-petal" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ff4d7e" />
            <stop offset="100%" stopColor="#ff9a5c" />
          </linearGradient>
        </defs>

        {/* 꽃잎 네 장. 같은 모양을 90도씩 돌려 겹친다.
            투명도를 조금 낮춰 겹친 부분이 진해지면서 깊이가 생긴다. */}
        <g fill="url(#skala-petal)" opacity="0.88">
          <ellipse cx="24" cy="14" rx="8.5" ry="13" />
          <ellipse cx="24" cy="34" rx="8.5" ry="13" />
          <ellipse cx="14" cy="24" rx="13" ry="8.5" />
          <ellipse cx="34" cy="24" rx="13" ry="8.5" />
        </g>

        {/* 가운데를 흰색으로 비운다. 작은 크기에서 형태가 뭉개지지 않게 하는 장치다. */}
        <circle cx="24" cy="24" r="5.5" fill="#fff" />
        <circle cx="24" cy="24" r="2.2" fill="url(#skala-petal)" />
      </svg>

      <div style={{ lineHeight: 1.05 }}>
        <div
          style={{
            fontSize: title,
            fontWeight: 800,
            letterSpacing: '-0.4px',
            color: '#17171c',
          }}
        >
          SKALA
        </div>
        <div
          style={{
            fontSize: big ? 10 : 8.5,
            fontWeight: 700,
            letterSpacing: big ? '5.5px' : '4px',
            color: '#ff4d7e',
            marginTop: 1,
          }}
        >
          BEAUTY
        </div>
      </div>
    </div>
  )
}
