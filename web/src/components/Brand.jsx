/**
 * SKALA BEAUTY 워드마크.
 *
 * <p>SK의 공식 로고를 가져다 쓰지 않았다. 실제 기업 상표를 복제하는 것은
 * 과제물이라 해도 적절하지 않아서, 이름만 빌려 새로 그렸다.
 *
 * <p>모양은 뷰티 커머스의 관례를 따랐다. 둥근 사각형 안의 모노그램과
 * 자간을 넓힌 워드마크는 이 업계에서 흔한 조합이다. 그라디언트는
 * 핑크에서 코럴로 흐르게 해 화장품 카테고리의 색감을 맞췄다.
 *
 * <p>SVG로 둔 이유는 이미지 파일보다 다루기 쉬워서다. 색과 크기를 코드에서 바꿀 수 있고
 * 어느 해상도에서 캡처해도 글자가 뭉개지지 않는다.
 */
export default function Brand({ size = 'md' }) {
  const big = size === 'lg'
  const box = big ? 44 : 30
  const title = big ? 30 : 19

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: big ? 12 : 9 }}>
      <svg width={box} height={box} viewBox="0 0 48 48" aria-hidden="true">
        <defs>
          <linearGradient id="skala-mark" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ff4d7e" />
            <stop offset="100%" stopColor="#ff9a5c" />
          </linearGradient>
        </defs>
        {/* 둥근 사각형. rx를 크게 줘서 앱 아이콘 같은 인상을 만든다. */}
        <rect x="0" y="0" width="48" height="48" rx="14" fill="url(#skala-mark)" />
        {/* 모노그램 S. 화장품 용기의 곡선을 연상시키도록 끝을 둥글게 처리했다. */}
        <path
          d="M31.5 17.2c-1.6-2.2-4.2-3.5-7.3-3.5-4.6 0-7.6 2.4-7.6 5.9 0 3.1 2.2 4.8 6.6 5.7l2.4.5c2.3.5 3.2 1.2 3.2 2.5 0 1.7-1.7 2.8-4.4 2.8-2.7 0-4.8-1.1-6.1-3.1l-3.4 2.6c1.9 2.9 5.2 4.6 9.3 4.6 5 0 8.3-2.5 8.3-6.3 0-3.3-2.1-5.1-6.8-6.1l-2.4-.5c-2.1-.4-3-1.1-3-2.3 0-1.5 1.5-2.5 3.9-2.5 2.2 0 3.9.9 4.9 2.4z"
          fill="#fff"
        />
      </svg>

      <div style={{ lineHeight: 1.05 }}>
        <div
          style={{
            fontSize: title,
            fontWeight: 800,
            letterSpacing: '-0.5px',
            background: 'linear-gradient(92deg,#ff3d6e,#ff8a5c)',
            WebkitBackgroundClip: 'text',
            backgroundClip: 'text',
            color: 'transparent',
          }}
        >
          SKALA
        </div>
        <div
          style={{
            fontSize: big ? 11 : 9,
            fontWeight: 700,
            letterSpacing: big ? '6px' : '4.5px',
            color: '#b9b9c4',
            marginTop: 1,
          }}
        >
          BEAUTY
        </div>
      </div>
    </div>
  )
}
