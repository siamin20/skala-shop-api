import { useCallback, useEffect, useState } from 'react'

const KEY = 'skala-beauty:cart'

/**
 * 장바구니. (D37)
 *
 * <h2>왜 서버가 아니라 브라우저에 두는가</h2>
 *
 * <p>장바구니는 <b>아직 주문이 아니다.</b> 서버에 두면 테이블이 하나 늘고
 * 비운 항목을 정리하는 배치가 필요해진다. 실제로 대부분의 장바구니는 결제되지 않는다.
 *
 * <p>대신 기기 간에 공유되지 않는다. 휴대폰에서 담은 것이 PC에 없다.
 * 그 불편을 감수할 만한 규모라고 봤다. 로그인 사용자가 여러 기기를 오가는 것이
 * 흔해지면 그때 서버로 옮기면 된다.
 *
 * <h2>계정마다 따로 둔다</h2>
 *
 * <p>키에 아이디를 붙인다. 공용 PC에서 다른 사람이 로그인했을 때
 * <b>앞사람 장바구니가 그대로 보이면</b> 무엇을 샀는지 드러난다.
 */
export default function useCart(customerId) {
  const key = customerId ? `${KEY}:${customerId}` : null
  const [items, setItems] = useState([])

  useEffect(() => {
    if (!key) {
      setItems([])
      return
    }
    try {
      setItems(JSON.parse(localStorage.getItem(key) ?? '[]'))
    } catch {
      // 저장된 값이 깨졌으면 버린다. 장바구니 때문에 앱이 뜨지 않으면 안 된다.
      setItems([])
    }
  }, [key])

  const persist = useCallback((next) => {
    setItems(next)
    if (key) localStorage.setItem(key, JSON.stringify(next))
  }, [key])

  const add = useCallback((product, quantity = 1) => {
    // 이미 담긴 상품이면 수량만 늘린다. 같은 상품이 두 줄로 보이면 헷갈린다.
    const found = items.find((i) => i.productId === product.id)
    persist(found
      ? items.map((i) => i.productId === product.id
          ? { ...i, quantity: i.quantity + quantity } : i)
      : [...items, {
          productId: product.id, name: product.name,
          price: product.price, quantity, stock: product.stock,
        }])
  }, [items, persist])

  const setQuantity = useCallback((productId, quantity) => {
    persist(items.map((i) => i.productId === productId
      ? { ...i, quantity: Math.max(1, quantity) } : i))
  }, [items, persist])

  const remove = useCallback((productId) => {
    persist(items.filter((i) => i.productId !== productId))
  }, [items, persist])

  const clear = useCallback(() => persist([]), [persist])

  const count = items.reduce((sum, i) => sum + i.quantity, 0)
  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0)

  return { items, add, setQuantity, remove, clear, count, total }
}
