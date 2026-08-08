import { useEffect, useState } from 'react'

/**
 * 스크롤 방향에 따라 헤더를 감출지 정한다. (D38)
 *
 * <p>목록을 훑어 내려갈 때는 헤더가 자리를 차지한다. 반대로 위로 올릴 때는
 * <b>대개 메뉴를 찾는 중</b>이라 헤더가 바로 나와야 한다.
 * 모바일 커머스가 공통으로 쓰는 동작이다.
 *
 * <p>임계값을 두는 이유: 스크롤 값은 1~2px씩 흔들린다. 그대로 반응하면 헤더가 떨린다.
 * 일정 거리 이상 움직였을 때만 방향이 바뀐 것으로 본다.
 *
 * <p>맨 위 근처에서는 항상 보여준다. 페이지를 열자마자 헤더가 없으면
 * 사용자가 여기가 어디인지 알 수 없다.
 */
export default function useScrollDirection(threshold = 8) {
  const [hidden, setHidden] = useState(false)

  useEffect(() => {
    let last = window.scrollY
    let ticking = false

    const update = () => {
      const y = window.scrollY

      if (y < 80) {
        // 맨 위 근처면 무조건 보여준다.
        setHidden(false)
      } else if (Math.abs(y - last) > threshold) {
        // 내려가면 감추고 올라오면 보여준다.
        setHidden(y > last)
      }

      last = y
      ticking = false
    }

    const onScroll = () => {
      // 스크롤 이벤트는 초당 수십 번 온다. 그때마다 상태를 바꾸면 화면이 계속 다시 그려진다.
      // 다음 프레임에 한 번만 계산하도록 묶는다.
      if (!ticking) {
        ticking = true
        requestAnimationFrame(update)
      }
    }

    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [threshold])

  return hidden
}
