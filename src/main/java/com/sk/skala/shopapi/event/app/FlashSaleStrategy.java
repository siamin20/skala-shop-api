package com.sk.skala.shopapi.event.app;

/**
 * 선착순 수량을 지키는 네 가지 방법. (D23)
 *
 * <p>같은 문제를 서로 다르게 푼다. 어느 하나가 항상 옳지 않아서 넷을 모두 구현하고
 * 같은 조건에서 재봤다. 측정 결과는 {@code docs/04-concurrency.md}에 있다.
 */
public enum FlashSaleStrategy {

    /**
     * 낙관적 락. {@code @Version}으로 충돌을 감지하고 재시도한다.
     *
     * <p>경합이 드물다는 전제 위의 전략이다. 선착순 이벤트는 그 전제가 깨지는 곳이라
     * <b>일부러 맞지 않는 도구를 넣어본 것</b>이다. 얼마나 나빠지는지 재기 위해서다.
     */
    OPTIMISTIC,

    /** 비관적 락. {@code SELECT ... FOR UPDATE}로 행을 잡는다. 정확하지만 한 줄로 선다. */
    PESSIMISTIC,

    /**
     * 조건부 원자 UPDATE. 읽고-검사하고-쓰기를 한 문장으로 합친다.
     *
     * <p>교재가 다루지 않는 방법이다. 락을 명시하지 않는데도 갱신 유실이 성립하지 않는다.
     */
    ATOMIC_UPDATE,

    /** Redis {@code DECRBY}. DB 바깥의 원자 카운터로 입장을 통제한다. */
    REDIS
}
