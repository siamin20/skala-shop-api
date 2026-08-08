package com.sk.skala.shopapi.event.app;

/**
 * 선착순 수량을 차감하는 방법. (D23)
 *
 * <p>구현체마다 동시성을 다루는 방식이 다르다. 서비스는 어느 방식인지 모른 채 호출하고,
 * 무엇을 쓸지는 설정이 정한다. 이렇게 나눠두면 <b>같은 조건에서 네 방식을 바꿔 끼우며
 * 측정</b>할 수 있다. 측정이 이 인터페이스의 존재 이유다.
 */
public interface FlashSaleClaimStrategy {

    FlashSaleStrategy type();

    /**
     * 이벤트를 시작하기 전 준비 작업.
     *
     * <p>DB 기반 전략은 할 일이 없다. Redis 전략만 카운터를 미리 채운다.
     * 기본 구현을 비워둔 이유는, 준비가 필요 없는 전략까지 빈 메서드를 쓰게 하지 않기 위해서다.
     */
    default void prepare(Long flashSaleId, int totalQuantity) {
        // 기본은 아무것도 하지 않는다.
    }

    /**
     * 수량을 차감한다.
     *
     * @throws com.sk.skala.shopapi.global.error.BusinessException 수량이 없으면 {@code SOLD_OUT}
     */
    void claim(Long flashSaleId, int quantity);

    /** 취소로 수량을 되돌린다. */
    void release(Long flashSaleId, int quantity);
}
