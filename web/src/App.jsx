import { useCallback, useEffect, useRef, useState } from 'react'
import { api, getAccessToken, refresh, subscribeToken } from './api/client.js'
import CartSheet from './components/CartSheet.jsx'
import ConfirmDialog from './components/ConfirmDialog.jsx'
import CheckoutPage from './components/CheckoutPage.jsx'
import FlashSales from './components/FlashSales.jsx'
import Header from './components/Header.jsx'
import Home from './components/Home.jsx'
import Login from './components/Login.jsx'
import MyPage from './components/MyPage.jsx'
import ProductGrid from './components/ProductGrid.jsx'
import SideMenu from './components/SideMenu.jsx'
import Toast from './components/Toast.jsx'
import WaitingRoomModal from './components/WaitingRoomModal.jsx'
import useCart from './hooks/useCart.js'
import useScrollDirection from './hooks/useScrollDirection.js'
import { preferences } from './auth/preferences.js'

const PAGE_SIZE = 10

const SORTS = [
  { key: 'BEST', label: '판매순' },
  { key: 'LATEST', label: '신상품' },
  { key: 'PRICE_ASC', label: '낮은 가격순' },
  { key: 'PRICE_DESC', label: '높은 가격순' },
]

export default function App() {
  const [me, setMe] = useState(null)
  const [booting, setBooting] = useState(true)
  const [view, setView] = useState('home')          // home | shop | event | mypage | checkout
  const [best, setBest] = useState([])              // 홈의 인기 상품
  const [filter, setFilter] = useState({})          // { category, subcategory }
  const [sort, setSort] = useState('BEST')
  const [page, setPage] = useState(0)

  const [categories, setCategories] = useState([])
  const [products, setProducts] = useState(null)
  const [orders, setOrders] = useState(null)
  const [sales, setSales] = useState([])
  const [addresses, setAddresses] = useState([])

  const [menuOpen, setMenuOpen] = useState(false)
  const [cartOpen, setCartOpen] = useState(false)
  const [checkout, setCheckout] = useState(null)
  const [addConfirm, setAddConfirm] = useState(null)   // 장바구니 담기 확인
  const [queue, setQueue] = useState(null)
  const [toast, setToast] = useState(null)
  const [busy, setBusy] = useState(false)

  const headerHidden = useScrollDirection()
  const cart = useCart(me?.customerId)
  const pollTimer = useRef(null)

  const point = orders?.point ?? 0

  /**
   * 새로고침해도 로그인이 유지된다.
   *
   * 액세스 토큰은 메모리에만 있어 사라지지만, HttpOnly 쿠키의 리프레시 토큰으로
   * 다시 받아온다. 저장하지 않고 갱신으로 푸는 방식이다. (D18)
   */
  useEffect(() => {
    subscribeToken((token) => { if (!token) setMe(null) })
    ;(async () => {
      // 자동 로그인을 꺼뒀으면 쿠키가 있어도 쓰지 않는다.
      // 남아 있는 쿠키는 지운다. 안 쓸 토큰을 14일 동안 들고 있을 이유가 없고,
      // 공용 기기에서 그 쿠키가 그대로 남으면 자동 로그인을 끈 의미가 사라진다. (D39)
      if (!preferences.autoLogin()) {
        await api.logout().catch(() => {})
        setBooting(false)
        return
      }

      if (await refresh()) {
        try { setMe(await api.me()) } catch { /* 갱신됐는데 조회 실패면 비로그인으로 둔다 */ }
      }
      setBooting(false)
    })()
  }, [])

  useEffect(() => () => clearTimeout(pollTimer.current), [])

  function showError(e) {
    const detail = e?.errors ? Object.values(e.errors).join(' · ') : null
    setToast({ type: 'error', message: e?.message ?? '요청에 실패했습니다', detail })
  }

  /** 목록을 다시 읽는다. 필터·정렬·페이지가 바뀔 때마다 서버에 다시 묻는다. */
  const loadProducts = useCallback(async () => {
    const data = await api.products({ page, size: PAGE_SIZE, sort, ...filter })
    setProducts(data)
  }, [page, sort, filter])

  /** 로그인 상태에 딸린 것들. 주문·배송지·특가. */
  const loadMine = useCallback(async () => {
    const [saleList, orderList] = await Promise.all([api.flashSales(), api.orders()])
    setSales(saleList)
    setOrders(orderList)
    try { setAddresses(await api.addresses()) } catch { setAddresses([]) }
  }, [])

  useEffect(() => {
    if (booting || !me) return
    api.categories().then(setCategories).catch(showError)
    // 홈의 인기 상품. 목록과 정렬이 달라 따로 읽는다.
    api.products({ page: 0, size: 5, sort: 'BEST' })
      .then((d) => setBest(d.content)).catch(() => {})
    loadMine().catch(showError)
  }, [booting, me, loadMine])

  useEffect(() => {
    if (booting || !me) return
    loadProducts().catch(showError)
  }, [booting, me, loadProducts])

  /** 필터가 바뀌면 첫 페이지로 돌아간다. 3페이지를 보다 분류를 바꾸면 빈 화면이 뜬다. */
  function selectCategory(next) {
    setFilter(next)
    setPage(0)
    setView('shop')
    // 분류를 바꾸면 목록 맨 위부터 보는 것이 자연스럽다.
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function run(action, successMessage) {
    setBusy(true)
    try {
      await action()
      await Promise.all([loadProducts(), loadMine()])
      if (successMessage) setToast({ type: 'ok', message: successMessage })
    } catch (e) {
      showError(e)
      Promise.all([loadProducts(), loadMine()]).catch(() => {})
    } finally {
      setBusy(false)
    }
  }

  // ── 특가: 대기열 → 주문서 ──

  async function joinFlashSale(sale) {
    setBusy(true)
    try {
      const ticket = await api.enterQueue(sale.id)
      if (ticket.admitted) openFlashCheckout(sale)
      else { setQueue({ sale, ticket }); pollQueue(sale) }
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  function pollQueue(sale) {
    clearTimeout(pollTimer.current)
    pollTimer.current = setTimeout(async () => {
      try {
        const ticket = await api.queuePosition(sale.id)
        if (ticket.admitted) { setQueue(null); openFlashCheckout(sale) }
        else { setQueue({ sale, ticket }); pollQueue(sale) }
      } catch (e) {
        setQueue(null)
        showError(e)
      }
    }, 1200)
  }

  /**
   * 주문서로 넘어간다. (D41)
   *
   * 창을 띄우는 대신 화면을 바꾼다. 이전 화면을 기억해 뒀다가 뒤로 가기에서 쓴다.
   * 기억하지 않으면 특가에서 들어왔든 장바구니에서 들어왔든 항상 홈으로 튕긴다.
   */
  function openCheckout(payload) {
    setCheckout({ ...payload, from: view })
    setView('checkout')
    window.scrollTo({ top: 0 })
  }

  function openFlashCheckout(sale) {
    openCheckout({
      kind: 'flash',
      sale,
      items: [{ productId: sale.productId, name: sale.productName, price: sale.price, quantity: 1 }],
    })
  }

  async function cancelQueue() {
    clearTimeout(pollTimer.current)
    const sale = queue?.sale
    setQueue(null)
    if (sale) await api.leaveQueue(sale.id).catch(() => {})
  }

  // ── 결제 ──

  async function submitCheckout(payload) {
    const c = checkout
    setCheckout(null)
    // 결제를 시작하면 주문서에서 나온다. 결제 중에 뒤로 눌러 다시 제출하는 것을 막는다.
    setView(c.from === 'checkout' ? 'home' : c.from)

    if (c.kind === 'flash') {
      await run(async () => {
        try {
          // 특가는 수량 보호가 먼저다. 참여에 성공해야 결제로 넘어간다.
          await api.joinFlashSale(c.sale.id, 1)
        } finally {
          await api.leaveQueue(c.sale.id).catch(() => {})
        }
      }, `${c.sale.name} 구매가 완료되었습니다`)
      return
    }

    await run(async () => {
      const result = await api.checkout({
        items: c.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        paymentMethod: payload.paymentMethod,
        usePoint: payload.usePoint,
        card: payload.card,
        deliveryAddressId: payload.deliveryAddressId,
        delivery: payload.delivery,
      })
      cart.clear()
      const earned = result.earnedPoint
      setToast({
        type: 'ok',
        message: '결제가 완료되었습니다',
        detail: earned > 0
          ? `${earned.toLocaleString('ko-KR')}P가 적립되었습니다`
          : null,
      })
    })
  }

  function closeCheckout() {
    // 특가에서 뒤로 나가면 잡아둔 자리를 반드시 내놓는다.
    // 이걸 빠뜨리면 앞자리가 비지 않아 뒤에 선 사람이 계속 기다린다.
    if (checkout?.kind === 'flash') api.leaveQueue(checkout.sale.id).catch(() => {})
    setView(checkout?.from ?? 'home')
    setCheckout(null)
    window.scrollTo({ top: 0 })
  }

  /** 주문서에서 배송지를 새로 저장한다. 저장한 뒤 목록을 다시 읽어 화면에 반영한다. */
  async function addAddress(body) {
    const created = await api.addAddress(body)
    setAddresses(await api.addresses())
    return created
  }

  if (booting) return <div className="empty">불러오는 중…</div>

  /*
   * 로그인 화면에도 토스트를 함께 그린다.
   *
   * 예전에는 Login만 반환했다. 그런데 로그인·회원가입 실패는 showError로 토스트 상태를
   * 바꾸기만 하고, 그 토스트를 그리는 <Toast>는 로그인 뒤 화면에만 있었다.
   * 그래서 비밀번호를 틀려도 화면에 아무 일도 일어나지 않았다.
   *
   * 서버는 401을 정확히 돌려주고 있었고 클라이언트도 그것을 받고 있었다.
   * 보여줄 자리만 없었다. 실패를 조용히 삼키는 화면은 고장 난 것으로 보인다.
   */
  if (!me) {
    return (
      <>
        <Login onLoggedIn={setMe} onError={showError} />
        <Toast toast={toast} onClose={() => setToast(null)} />
      </>
    )
  }

  const totalPages = products?.totalPages ?? 0

  return (
    <>
      <Header
        hidden={headerHidden}
        view={view}
        current={filter}
        categories={categories}
        cartCount={cart.count}
        me={me}
        point={point}
        onHome={() => { setView('home'); setFilter({}); setPage(0); window.scrollTo({ top: 0 }) }}
        onNavigate={(v) => { setView(v); window.scrollTo({ top: 0 }) }}
        onSelectCategory={selectCategory}
        onOpenMenu={() => setMenuOpen(true)}
        onOpenCart={() => setCartOpen(true)}
      />

      <main className="wrap">
        {view === 'home' && (
          <Home
            sales={sales}
            best={best}
            categories={categories}
            onSelectCategory={selectCategory}
            onGoEvent={() => { setView('event'); window.scrollTo({ top: 0 }) }}
            onProduct={(p) => openCheckout({
              kind: 'cart',
              items: [{ productId: p.id, name: p.name, price: p.price, quantity: 1 }],
            })}
          />
        )}

        {view === 'shop' && (
          <>
            <div className="list-head">
              <span className="muted small">
                총 {products?.totalElements ?? 0}개
              </span>
              <div className="sorts">
                {SORTS.map((s) => (
                  <button
                    key={s.key}
                    className={sort === s.key ? 'on' : ''}
                    onClick={() => { setSort(s.key); setPage(0) }}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </div>

            <ProductGrid
              products={products?.content ?? []}
              busy={busy}
              onAdd={(product, quantity) => setAddConfirm({ product, quantity })}
              onBuyNow={(product, quantity) => openCheckout({
                kind: 'cart',
                items: [{
                  productId: product.id, name: product.name,
                  price: product.price, quantity,
                }],
              })}
            />

            {totalPages > 1 && (
              <nav className="pager">
                <button disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
                {Array.from({ length: totalPages }, (_, i) => (
                  <button key={i} className={i === page ? 'on' : ''} onClick={() => setPage(i)}>
                    {i + 1}
                  </button>
                ))}
                <button disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>
                  다음
                </button>
              </nav>
            )}
          </>
        )}

        {view === 'event' && (
          <FlashSales
            sales={sales}
            busy={busy}
            onRefresh={() => loadMine().catch(showError)}
            onJoin={joinFlashSale}
          />
        )}

        {view === 'checkout' && checkout && (
          <CheckoutPage
            items={checkout.items}
            point={point}
            addresses={addresses}
            busy={busy}
            onBack={closeCheckout}
            onSubmit={submitCheckout}
            onAddAddress={addAddress}
            onError={showError}
          />
        )}

        {view === 'mypage' && (
          <MyPage
            me={me}
            orders={orders}
            addresses={addresses}
            busy={busy}
            onCancel={(productId, quantity) => run(
              () => api.cancel(productId, quantity), '주문이 취소되었습니다')}
          />
        )}
      </main>

      <footer className="site-foot">
        <div className="foot-brand">SKALA BEAUTY</div>
        <p className="muted small">SKALA SHOP API · 최종 실습 과제</p>
        <button className="text-link" onClick={() => run(() => api.logout(), null)}>
          로그아웃
        </button>
      </footer>

      <SideMenu
        open={menuOpen}
        me={me}
        point={point}
        cartCount={cart.count}
        categories={categories}
        current={filter}
        onSelect={selectCategory}
        onClose={() => setMenuOpen(false)}
        onNavigate={setView}
        onOpenCart={() => setCartOpen(true)}
        onLogout={() => run(() => api.logout(), null)}
      />

      <CartSheet
        open={cartOpen}
        items={cart.items}
        total={cart.total}
        onClose={() => setCartOpen(false)}
        onQuantity={cart.setQuantity}
        onRemove={cart.remove}
        onCheckout={() => {
          setCartOpen(false)
          openCheckout({ kind: 'cart', items: cart.items })
        }}
      />

      <WaitingRoomModal
        ticket={queue?.ticket}
        saleName={queue?.sale?.name}
        onCancel={cancelQueue}
      />

      <ConfirmDialog
        open={!!addConfirm}
        title="장바구니에 담을까요?"
        message={addConfirm
          ? `${addConfirm.product.name} · ${addConfirm.quantity}개`
          : null}
        confirmLabel="담기"
        onCancel={() => setAddConfirm(null)}
        onConfirm={() => {
          cart.add(addConfirm.product, addConfirm.quantity)
          setToast({ type: 'ok', message: '장바구니에 담았습니다' })
          setAddConfirm(null)
        }}
      />

      <Toast toast={toast} onClose={() => setToast(null)} />
    </>
  )
}
