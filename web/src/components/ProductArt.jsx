/**
 * 상품 일러스트.
 *
 * <p>실제 상품 사진을 쓰지 않았다. 올리브영·무신사의 제품 이미지는 저작물이고,
 * 공개 저장소에 올리면 그대로 문제가 된다. 그래서 카테고리별 용기 모양을 직접 그렸다.
 *
 * <p>사진 대신 일러스트를 쓰는 것은 실제 커머스에서도 흔하다. 다만 사진만큼의 정보를
 * 담지 못하므로, 여기서는 <b>카테고리를 한눈에 구분하는 것</b>까지만 목표로 한다.
 *
 * <p>SVG로 그린 이유는 파일이 필요 없어서다. 이미지 파일을 두면 저장소가 무거워지고
 * 색을 바꿀 때마다 다시 만들어야 한다.
 */

/** 상품명에서 카테고리를 추정한다. 카테고리 컬럼을 두는 편이 정확하지만 명세에 없는 필드다. */
function categoryOf(name) {
  if (/앰플|세럼|에센스|오일/.test(name)) return 'dropper'
  if (/토너|스킨|미스트|샴푸|바디로션|워시/.test(name)) return 'bottle'
  if (/크림|밤|수딩젤/.test(name)) return 'jar'
  if (/클렌징|폼|선크림|마스카라|핸드/.test(name)) return 'tube'
  if (/립|틴트/.test(name)) return 'lipstick'
  if (/쿠션|파운데이션|섀도우|팔레트/.test(name)) return 'compact'
  if (/마스크|팩/.test(name)) return 'pouch'
  return 'bottle'
}

/** 카테고리마다 다른 배경·본체 색. 카드가 늘어섰을 때 색으로 먼저 구분된다. */
const PALETTE = {
  dropper:  { bg: '#fff1f4', body: '#ff85a8', cap: '#3b2b33', accent: '#ffd6e2' },
  bottle:   { bg: '#eef6ff', body: '#8fbcff', cap: '#2f3d55', accent: '#d5e6ff' },
  jar:      { bg: '#f3f0ff', body: '#b3a3ff', cap: '#efeaff', accent: '#d9d1ff' },
  tube:     { bg: '#eefaf6', body: '#7fd8bd', cap: '#2b4b41', accent: '#cdefe4' },
  lipstick: { bg: '#ffeef2', body: '#ff5a7d', cap: '#2b2028', accent: '#ffc9d6' },
  compact:  { bg: '#fdf0ff', body: '#e2a8f0', cap: '#f7e4fb', accent: '#f2d4f8' },
  pouch:    { bg: '#fff8e8', body: '#ffd27a', cap: '#6b5326', accent: '#ffe9bf' },
}

export default function ProductArt({ name, size = '100%' }) {
  const kind = categoryOf(name)
  const c = PALETTE[kind]

  return (
    <svg viewBox="0 0 120 120" width={size} height={size} role="img" aria-label={name}>
      <rect width="120" height="120" rx="14" fill={c.bg} />

      {/* 배경 장식. 비어 보이지 않게 하는 최소한의 요소다. */}
      <circle cx="96" cy="26" r="20" fill={c.accent} opacity=".55" />
      <circle cx="22" cy="98" r="14" fill={c.accent} opacity=".45" />

      {kind === 'dropper' && (
        <g>
          <rect x="48" y="44" width="24" height="46" rx="7" fill={c.body} />
          <rect x="53" y="52" width="14" height="26" rx="4" fill="#fff" opacity=".35" />
          <rect x="54" y="24" width="12" height="22" rx="4" fill={c.cap} />
          <rect x="50" y="38" width="20" height="8" rx="4" fill={c.cap} />
        </g>
      )}

      {kind === 'bottle' && (
        <g>
          <rect x="42" y="40" width="36" height="52" rx="9" fill={c.body} />
          <rect x="49" y="50" width="10" height="30" rx="5" fill="#fff" opacity=".35" />
          <rect x="52" y="24" width="16" height="18" rx="4" fill={c.cap} />
          <rect x="46" y="60" width="28" height="16" rx="3" fill="#fff" opacity=".7" />
        </g>
      )}

      {kind === 'jar' && (
        <g>
          <rect x="36" y="52" width="48" height="38" rx="11" fill={c.body} />
          <rect x="33" y="38" width="54" height="18" rx="8" fill={c.cap} />
          <ellipse cx="60" cy="38" rx="27" ry="7" fill="#fff" opacity=".55" />
          <rect x="45" y="66" width="30" height="12" rx="3" fill="#fff" opacity=".6" />
        </g>
      )}

      {kind === 'tube' && (
        <g>
          <path d="M44 40h32v42a10 10 0 0 1-10 10H54a10 10 0 0 1-10-10z" fill={c.body} />
          <path d="M44 40l6-14h20l6 14z" fill={c.body} opacity=".75" />
          <rect x="54" y="18" width="12" height="10" rx="3" fill={c.cap} />
          <rect x="50" y="58" width="20" height="14" rx="3" fill="#fff" opacity=".65" />
        </g>
      )}

      {kind === 'lipstick' && (
        <g>
          <rect x="50" y="54" width="20" height="38" rx="4" fill={c.cap} />
          <rect x="52" y="46" width="16" height="10" rx="2" fill="#e9e3ea" />
          <path d="M54 46V30a6 6 0 0 1 12 0v16z" fill={c.body} />
          <path d="M54 33c3-5 9-6 12-2v-1a6 6 0 0 0-12 0z" fill="#fff" opacity=".45" />
        </g>
      )}

      {kind === 'compact' && (
        <g>
          <circle cx="60" cy="66" r="26" fill={c.body} />
          <circle cx="60" cy="66" r="18" fill="#fff" opacity=".5" />
          <path d="M34 52a26 26 0 0 1 52 0z" fill={c.cap} />
          <circle cx="60" cy="66" r="8" fill="#fff" opacity=".8" />
        </g>
      )}

      {kind === 'pouch' && (
        <g>
          <rect x="36" y="34" width="48" height="56" rx="6" fill={c.body} />
          <rect x="36" y="34" width="48" height="10" rx="3" fill={c.cap} opacity=".35" />
          <rect x="45" y="54" width="30" height="20" rx="4" fill="#fff" opacity=".6" />
          <circle cx="60" cy="64" r="5" fill={c.body} opacity=".55" />
        </g>
      )}
    </svg>
  )
}
