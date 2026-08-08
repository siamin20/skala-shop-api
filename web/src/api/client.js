/**
 * API 호출 계층.
 *
 * 화면 코드가 fetch를 직접 부르지 않게 한 곳으로 모은다. 토큰을 붙이고,
 * 만료되면 갱신하고, 에러를 사람이 읽을 문장으로 바꾸는 일이 전부 여기서 일어난다.
 * 화면마다 흩어지면 한 곳만 빠뜨려도 그 화면에서만 로그인이 풀린다.
 */

/**
 * 액세스 토큰은 메모리에만 둔다. (D18)
 *
 * localStorage에 두면 XSS 한 번에 통째로 털린다. 저장된 값은 스크립트가 언제든
 * 읽을 수 있기 때문이다. 모듈 스코프 변수는 새로고침하면 사라지지만, 그때는
 * HttpOnly 쿠키에 담긴 리프레시 토큰으로 다시 받아오면 된다.
 *
 * 즉 "새로고침하면 로그인이 풀리는" 문제를 저장이 아니라 갱신으로 푼다.
 */
let accessToken = null

/** 토큰이 바뀔 때 화면에 알린다. 로그인·로그아웃 상태를 App이 따라가게 하려는 것이다. */
let onTokenChange = () => {}

export function setAccessToken(token) {
  accessToken = token
  onTokenChange(token)
}

export function getAccessToken() {
  return accessToken
}

export function subscribeToken(listener) {
  onTokenChange = listener
}

/** 서버가 내려준 ProblemDetail을 사람이 읽을 문장으로 바꾼다. */
export class ApiError extends Error {
  constructor(status, body) {
    // 서버는 RFC 7807 형식으로 detail에 사람이 읽을 설명을 담는다. (D4)
    super(body?.detail || body?.title || `요청이 실패했습니다 (${status})`)
    this.status = status
    this.code = body?.code
    // 검증 실패는 필드별 메시지가 errors에 담긴다.
    this.errors = body?.errors
  }
}

async function parseBody(response) {
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/**
 * 실제 요청.
 *
 * 401을 받으면 리프레시로 한 번 되살려보고 원래 요청을 다시 보낸다.
 * `retry` 인자가 그 재시도 여부다. 이게 없으면 갱신도 401일 때 무한히 반복한다.
 */
async function request(path, { method = 'GET', body, retry = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`

  // 멱등성 키. 주문·취소·충전에 붙는다. (D20)
  // 사용자가 버튼을 두 번 눌러도 서버가 한 번만 처리하게 만드는 장치다.
  if (method === 'POST') headers['Idempotency-Key'] = crypto.randomUUID()

  const response = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    // 리프레시 쿠키를 주고받으려면 필요하다. 없으면 갱신이 영원히 실패한다.
    credentials: 'include',
  })

  if (response.status === 401 && retry) {
    // 액세스 토큰이 15분이라 사용 중에 만료되는 것이 정상이다.
    // 사용자에게 다시 로그인하라고 하지 않고 조용히 되살린다.
    const renewed = await refresh()
    if (renewed) return request(path, { method, body, retry: false })
  }

  const data = await parseBody(response)
  if (!response.ok) throw new ApiError(response.status, data)
  return data
}

/**
 * 리프레시 쿠키로 액세스 토큰을 다시 받는다.
 *
 * 실패해도 예외를 던지지 않는다. "아직 로그인하지 않았다"와 "세션이 끝났다"는
 * 둘 다 정상적인 상태라 화면이 오류로 다룰 일이 아니다.
 */
export async function refresh() {
  try {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })
    if (!response.ok) return false
    const data = await response.json()
    setAccessToken(data.accessToken)
    return true
  } catch {
    return false
  }
}

export const api = {
  signUp: (customerId, password) =>
    request('/api/customers', { method: 'POST', body: { customerId, password } }),

  async login(customerId, password) {
    const data = await request('/api/auth/login', {
      method: 'POST',
      body: { customerId, password },
    })
    setAccessToken(data.accessToken)
    return data
  },

  async logout() {
    try {
      await request('/api/auth/logout', { method: 'POST' })
    } finally {
      // 서버 호출이 실패해도 화면에서는 로그아웃시킨다.
      // 여기서 멈추면 사용자가 로그아웃한 줄 알고 자리를 뜬다.
      setAccessToken(null)
    }
  },

  me: () => request('/api/auth/me'),
  products: ({ page = 0, size = 10, sort = 'LATEST', category, subcategory } = {}) => {
    const q = new URLSearchParams({ page, size, sort })
    if (category) q.set('category', category)
    if (subcategory) q.set('subcategory', subcategory)
    return request(`/api/products?${q}`)
  },

  categories: () => request('/api/products/categories'),
  orders: () => request('/api/orders'),

  order: (productId, quantity) =>
    request('/api/orders', { method: 'POST', body: { productId, quantity } }),

  cancel: (productId, quantity) =>
    request('/api/orders/cancel', { method: 'POST', body: { productId, quantity } }),

  // ── 배송지 (D34, D40) ──
  //
  // 배송지는 여러 개를 저장한다. 집과 회사를 번갈아 쓰는 사람이 매번 주소를 다시 치게 하면
  // 그게 곧 이탈이다. 목록의 첫 항목이 기본 배송지다(서버가 정렬해서 준다).
  addresses: () => request('/api/delivery-addresses'),

  addAddress: (body) => request('/api/delivery-addresses', { method: 'POST', body }),

  updateAddress: (id, body) =>
    request(`/api/delivery-addresses/${id}`, { method: 'PUT', body }),

  removeAddress: (id) =>
    request(`/api/delivery-addresses/${id}`, { method: 'DELETE' }),

  // ── 카드 결제 (D31, D32) ──
  checkout: (body) => request('/api/orders/checkout', { method: 'POST', body }),

  charge: (customerId, amount) =>
    request(`/api/customers/${encodeURIComponent(customerId)}/points`, {
      method: 'POST',
      body: { amount },
    }),

  flashSales: () => request('/api/flash-sales'),

  // ── 대기열 (D30) ──
  // 줄을 서고, 순번을 확인하고, 끝나면 빠진다.
  // 마지막 leave를 빠뜨리면 앞자리가 비지 않아 뒷사람이 영원히 기다린다.
  enterQueue: (flashSaleId) =>
    request(`/api/flash-sales/${flashSaleId}/queue`, { method: 'POST' }),

  queuePosition: (flashSaleId) => request(`/api/flash-sales/${flashSaleId}/queue`),

  leaveQueue: (flashSaleId) =>
    request(`/api/flash-sales/${flashSaleId}/queue`, { method: 'DELETE' }),

  joinFlashSale: (flashSaleId, quantity) =>
    request('/api/flash-sales/orders', {
      method: 'POST',
      body: { flashSaleId, quantity },
    }),
}
