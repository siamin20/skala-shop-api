/**
 * 상품 일러스트.
 *
 * <p>실제 상품 사진을 쓰지 않았다. 올리브영·무신사의 제품 이미지는 저작물이고,
 * 공개 저장소에 올리면 그대로 문제가 된다. 그래서 용기 모양을 직접 그렸다.
 *
 * <h2>같은 카테고리라도 다르게 보이게 한다</h2>
 *
 * <p>처음에는 카테고리마다 그림 하나씩만 뒀더니 <b>목록에서 같은 그림이 반복돼</b>
 * 상품이 구분되지 않았다. 크림 다섯 개가 전부 똑같은 통으로 보였다.
 *
 * <p>이름을 해시해서 색·비율·장식을 바꾼다. 같은 이름은 항상 같은 그림이 나오므로
 * 새로고침할 때마다 색이 바뀌지 않는다. 무작위였다면 목록을 다시 그릴 때마다
 * 상품이 다른 물건처럼 보였을 것이다.
 */

function categoryOf(name) {
  if (/앰플|세럼|에센스|오일/.test(name)) return 'dropper'
  if (/토너|스킨|미스트/.test(name)) return 'bottle'
  if (/샴푸|바디로션|워시|트리트먼트/.test(name)) return 'pump'
  if (/크림|밤|수딩젤/.test(name)) return 'jar'
  if (/클렌징|폼|선크림|핸드/.test(name)) return 'tube'
  if (/립|틴트/.test(name)) return 'lipstick'
  if (/쿠션|파운데이션/.test(name)) return 'compact'
  if (/섀도우|팔레트/.test(name)) return 'palette'
  if (/마스카라|아이라이너/.test(name)) return 'mascara'
  if (/마스크|팩/.test(name)) return 'pouch'
  return 'bottle'
}

/** 이름을 안정적인 숫자로 바꾼다. 같은 이름은 항상 같은 값이다. */
function hash(name) {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) | 0
  return Math.abs(h)
}

/** 카테고리별 기본 색조에 이름 해시로 변주를 준다. */
const HUES = {
  dropper: [345, 12], bottle: [205, 190], pump: [168, 200], jar: [265, 285],
  tube: [155, 140], lipstick: [350, 330], compact: [292, 310],
  palette: [30, 45], mascara: [225, 250], pouch: [40, 20],
}

export default function ProductArt({ name, size = '100%' }) {
  const kind = categoryOf(name)
  const h = hash(name)
  const [hueA, hueB] = HUES[kind]

  // 두 색조 사이에서 이름에 따라 다른 지점을 고른다.
  const hue = hueA + ((h % 100) / 100) * (hueB - hueA)
  const bg = `hsl(${hue} 70% 96%)`
  const body = `hsl(${hue} 62% 68%)`
  const cap = `hsl(${hue} 42% 26%)`
  const soft = `hsl(${hue} 66% 90%)`

  // 장식 위치와 크기도 흔든다. 같은 카테고리 카드가 나란히 있어도 달라 보인다.
  const dot1 = 14 + (h % 9)
  const dot2 = 9 + ((h >> 3) % 7)
  const tall = 0.9 + ((h >> 5) % 20) / 100   // 용기 높이 변주

  return (
    <svg viewBox="0 0 120 120" width={size} height={size} role="img" aria-label={name}>
      <rect width="120" height="120" rx="12" fill={bg} />
      <circle cx={94 + (h % 6)} cy={24 + (h % 8)} r={dot1} fill={soft} />
      <circle cx={20 + ((h >> 2) % 8)} cy={98 - ((h >> 4) % 6)} r={dot2} fill={soft} />

      <g transform={`translate(60 62) scale(1 ${tall}) translate(-60 -62)`}>
        {kind === 'dropper' && (
          <g>
            <rect x="48" y="44" width="24" height="46" rx="7" fill={body} />
            <rect x="53" y="52" width="13" height="26" rx="4" fill="#fff" opacity=".38" />
            <rect x="54" y="24" width="12" height="22" rx="4" fill={cap} />
            <rect x="50" y="38" width="20" height="8" rx="4" fill={cap} />
          </g>
        )}
        {kind === 'bottle' && (
          <g>
            <rect x="43" y="40" width="34" height="52" rx="9" fill={body} />
            <rect x="49" y="50" width="9" height="30" rx="4" fill="#fff" opacity=".38" />
            <rect x="52" y="24" width="16" height="18" rx="4" fill={cap} />
            <rect x="47" y="62" width="26" height="14" rx="3" fill="#fff" opacity=".62" />
          </g>
        )}
        {kind === 'pump' && (
          <g>
            <rect x="42" y="44" width="36" height="48" rx="8" fill={body} />
            <rect x="54" y="26" width="12" height="18" rx="3" fill={cap} />
            <path d="M54 30h-9a4 4 0 0 0-4 4v3" stroke={cap} strokeWidth="4" fill="none" strokeLinecap="round" />
            <rect x="47" y="60" width="26" height="16" rx="3" fill="#fff" opacity=".6" />
          </g>
        )}
        {kind === 'jar' && (
          <g>
            <rect x="36" y="54" width="48" height="36" rx="11" fill={body} />
            <rect x="33" y="40" width="54" height="18" rx="8" fill={cap} />
            <ellipse cx="60" cy="40" rx="27" ry="7" fill="#fff" opacity=".5" />
            <rect x="46" y="68" width="28" height="11" rx="3" fill="#fff" opacity=".6" />
          </g>
        )}
        {kind === 'tube' && (
          <g>
            <path d="M44 40h32v42a10 10 0 0 1-10 10H54a10 10 0 0 1-10-10z" fill={body} />
            <path d="M44 40l6-14h20l6 14z" fill={body} opacity=".72" />
            <rect x="54" y="18" width="12" height="10" rx="3" fill={cap} />
            <rect x="50" y="58" width="20" height="13" rx="3" fill="#fff" opacity=".6" />
          </g>
        )}
        {kind === 'lipstick' && (
          <g>
            <rect x="50" y="54" width="20" height="38" rx="4" fill={cap} />
            <rect x="52" y="46" width="16" height="10" rx="2" fill="#e9e3ea" />
            <path d="M54 46V30a6 6 0 0 1 12 0v16z" fill={body} />
            <path d="M54 33c3-5 9-6 12-2v-1a6 6 0 0 0-12 0z" fill="#fff" opacity=".45" />
          </g>
        )}
        {kind === 'compact' && (
          <g>
            <circle cx="60" cy="66" r="25" fill={body} />
            <circle cx="60" cy="66" r="17" fill="#fff" opacity=".48" />
            <path d="M35 52a25 25 0 0 1 50 0z" fill={cap} />
            <circle cx="60" cy="66" r="7" fill="#fff" opacity=".8" />
          </g>
        )}
        {kind === 'palette' && (
          <g>
            <rect x="30" y="46" width="60" height="40" rx="7" fill={cap} />
            {[0, 1, 2].map((r) => [0, 1, 2].map((c) => (
              <rect key={`${r}${c}`} x={37 + c * 17} y={53 + r * 11} width="13" height="8" rx="2"
                    fill={`hsl(${hue + (r * 3 + c) * 9} 62% ${62 + ((r + c) % 3) * 8}%)`} />
            )))}
          </g>
        )}
        {kind === 'mascara' && (
          <g>
            <rect x="52" y="46" width="16" height="46" rx="6" fill={body} />
            <rect x="54" y="22" width="12" height="26" rx="4" fill={cap} />
            <rect x="56" y="56" width="8" height="22" rx="2" fill="#fff" opacity=".45" />
          </g>
        )}
        {kind === 'pouch' && (
          <g>
            <rect x="36" y="34" width="48" height="56" rx="6" fill={body} />
            <rect x="36" y="34" width="48" height="9" rx="3" fill={cap} opacity=".4" />
            <rect x="45" y="54" width="30" height="20" rx="4" fill="#fff" opacity=".58" />
            <circle cx="60" cy="64" r="5" fill={body} opacity=".6" />
          </g>
        )}
      </g>
    </svg>
  )
}
