import { useCallback, useEffect, useRef, useState } from 'react'
import { api, refresh, subscribeToken, getAccessToken } from './api/client.js'
import Brand from './components/Brand.jsx'
import ConfirmDialog from './components/ConfirmDialog.jsx'
import FlashSales from './components/FlashSales.jsx'
import Login from './components/Login.jsx'
import Orders from './components/Orders.jsx'
import ProductGrid from './components/ProductGrid.jsx'
import Toast from './components/Toast.jsx'
import WaitingRoomModal from './components/WaitingRoomModal.jsx'

const won = (n) => n.toLocaleString('ko-KR')

export default function App() {
  const [me, setMe] = useState(null)
  const [tab, setTab] = useState('event')   // 특가를 첫 화면으로. 이 프로젝트의 얼굴이다.
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState(null)
  const [sales, setSales] = useState([])
  const [toast, setToast] = useState(null)
  const [busy, setBusy] = useState(false)
  const [booting, setBooting] = useState(true)

  /** 결제 직전 확인 창. 무엇을 얼마에 사는지 보여주고 한 번 더 묻는다. */
  const [confirm, setConfirm] = useState(null)

  /** 대기열 상태. 순번을 받으면 화면을 덮는다. */
  const [queue, setQueue] = useState(null)
  const pollTimer = useRef(null)

  const point = orders?.point ?? 0

  /**
   * 새로고침해도 로그인이 유지되게 한다.
   *
   * 액세스 토큰은 메모리에만 있어 새로고침하면 사라진다(D18).
   * HttpOnly 쿠키의 리프레시 토큰으로 다시 받아온다. 저장하지 않고 다시 받는 방식이다.
   */
  useEffect(() => {
    subscribeToken((token) => { if (!token) setMe(null) })
    ;(async () => {
      if (await refresh()) {
        try { setMe(await api.me()) } catch { /* 갱신은 됐는데 조회 실패면 비로그인으로 둔다 */ }
      }
      setBooting(false)
    })()
  }, [])

  /** 대기 중에 화면을 떠나면 타이머가 남는다. 반드시 정리한다. */
  useEffect(() => () => clearTimeout(pollTimer.current), [])

  const reload = useCallback(async () => {
    const [productPage, saleList] = await Promise.all([api.products(0, 40), api.flashSales()])
    setProducts(productPage.content)
    setSales(saleList)
    if (getAccessToken()) setOrders(await api.orders())
  }, [])

  useEffect(() => { if (!booting) reload().catch(showError) }, [booting, me, reload])

  function showError(e) {
    // 검증 실패는 필드별로 이유가 다르다. 뭉뚱그리면 무엇을 고칠지 모른다.
    const detail = e?.errors ? Object.values(e.errors).join(' · ') : null
    setToast({ type: 'error', message: e?.message ?? '요청에 실패했습니다', detail })
  }

  /**
   * 모든 동작의 공통 실행기.
   *
   * 성공하든 실패하든 전체를 다시 읽는다. 주문 하나가 재고·적립금·주문내역·특가 수량을
   * 동시에 바꾸기 때문이다. 실패했을 때도 읽는 이유는, 품절이라 실패했다면
   * 화면의 재고도 이미 0일 것이기 때문이다.
   */
  async function run(action, successMessage) {
    setBusy(true)
    try {
      await action()
      await reload()
      if (successMessage) setToast({ type: 'ok', message: successMessage })
    } catch (e) {
      showError(e)
      reload().catch(() => {})
    } finally {
      setBusy(false)
    }
  }

  // ─────────────────── 특가: 대기열 → 확인 → 결제 ───────────────────

  /**
   * 특가 참여를 시작한다.
   *
   * 곧바로 결제하지 않는다. 먼저 줄을 서고, 순서가 되면 확인 창을 띄운다.
   * 선착순은 급하게 누르는 화면이라 오히려 확인 절차가 더 필요하다.
   */
  async function joinFlashSale(sale) {
    setBusy(true)
    try {
      const ticket = await api.enterQueue(sale.id)
      if (ticket.admitted) {
        openConfirm(sale)
      } else {
        setQueue({ sale, ticket })
        pollQueue(sale)
      }
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  /**
   * 순번을 주기적으로 확인한다.
   *
   * 1.2초 간격으로 둔 이유: 더 짧으면 대기 인원이 많을 때 조회 요청이 서버를 때린다.
   * 대기열은 서버를 지키려고 만든 것인데 그 조회가 서버를 괴롭히면 앞뒤가 안 맞는다.
   */
  function pollQueue(sale) {
    clearTimeout(pollTimer.current)
    pollTimer.current = setTimeout(async () => {
      try {
        const ticket = await api.queuePosition(sale.id)
        if (ticket.admitted) {
          setQueue(null)
          openConfirm(sale)
        } else {
          setQueue({ sale, ticket })
          pollQueue(sale)
        }
      } catch (e) {
        setQueue(null)
        showError(e)
      }
    }, 1200)
  }

  function openConfirm(sale) {
    setConfirm({
      kind: 'flash',
      sale,
      title: '특가 상품 결제',
      productName: sale.productName,
      price: sale.price,
      quantity: 1,
    })
  }

  async function cancelQueue() {
    clearTimeout(pollTimer.current)
    const sale = queue?.sale
    setQueue(null)
    if (sale) await api.leaveQueue(sale.id).catch(() => {})
  }

  /** 확인 창에서 결제를 누른 순간. */
  async function submitConfirm() {
    const c = confirm
    setConfirm(null)
    if (c.kind === 'flash') {
      await run(
        async () => {
          try {
            await api.joinFlashSale(c.sale.id, c.quantity)
          } finally {
            // 성공이든 실패든 줄에서 빠진다. 빠지지 않으면 뒷사람이 영원히 기다린다.
            await api.leaveQueue(c.sale.id).catch(() => {})
          }
        },
        `${c.productName} 구매가 완료되었습니다`)
    } else {
      await run(
        () => api.order(c.product.id, c.quantity),
        `${c.productName} ${c.quantity}개를 구매했습니다`)
    }
  }

  function closeConfirm() {
    // 특가는 확인 창을 닫을 때도 줄에서 빠져야 한다.
    if (confirm?.kind === 'flash') api.leaveQueue(confirm.sale.id).catch(() => {})
    setConfirm(null)
  }

  if (booting) return <div className="empty">불러오는 중…</div>
  if (!me) return <Login onLoggedIn={setMe} />

  return (
    <>
      <div className="topbar">
        <div className="topbar-inner">
          <Brand />
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div className="wallet">
              <span className="who">{me.customerId}</span>
              <span className="amount">{won(point)}P</span>
            </div>
            <button className="line tiny" disabled={busy}
                    onClick={() => run(() => api.charge(me.customerId, 100000), '100,000P가 충전되었습니다')}>
              충전
            </button>
            <button className="line tiny" onClick={() => run(() => api.logout(), null)}>
              로그아웃
            </button>
          </div>
        </div>
      </div>

      <div className="wrap">
        <div className="tabs">
          <button className={tab === 'event' ? 'on' : ''} onClick={() => setTab('event')}>
            오늘의 특가
          </button>
          <button className={tab === 'shop' ? 'on' : ''} onClick={() => setTab('shop')}>
            전체 상품
          </button>
          <button className={tab === 'orders' ? 'on' : ''} onClick={() => setTab('orders')}>
            주문 내역
          </button>
        </div>

        {tab === 'event' && (
          <div className="section">
            <h2>오늘의 특가</h2>
            <p className="sub">준비된 수량이 소진되면 자동으로 마감됩니다</p>
            <FlashSales
              sales={sales}
              busy={busy}
              onRefresh={() => reload().catch(showError)}
              onJoin={joinFlashSale}
            />
          </div>
        )}

        {tab === 'shop' && (
          <div className="section">
            <h2>전체 상품</h2>
            <p className="sub">지금 인기 있는 뷰티 아이템</p>
            <ProductGrid
              products={products}
              busy={busy}
              onOrder={(product, quantity) => setConfirm({
                kind: 'product',
                product,
                title: '구매 확인',
                productName: product.name,
                price: product.price,
                quantity,
              })}
            />
          </div>
        )}

        {tab === 'orders' && (
          <div className="section">
            <h2>주문 내역</h2>
            <p className="sub">수량을 지정해 부분 취소할 수 있습니다</p>
            <Orders
              orders={orders}
              busy={busy}
              onCancel={(productId, quantity) => run(
                () => api.cancel(productId, quantity),
                '주문이 취소되었습니다')}
            />
          </div>
        )}
      </div>

      <WaitingRoomModal
        ticket={queue?.ticket}
        saleName={queue?.sale?.name}
        onCancel={cancelQueue}
      />

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title}
        productName={confirm?.productName}
        price={confirm?.price ?? 0}
        quantity={confirm?.quantity ?? 1}
        point={point}
        busy={busy}
        onConfirm={submitConfirm}
        onCancel={closeConfirm}
      />

      <Toast toast={toast} onClose={() => setToast(null)} />
    </>
  )
}
