package com.sk.skala.shopapi.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 주문·취소 요청.
 *
 * <p>과제 명세의 {@code OrderRequest}에 해당한다. 주문과 취소가 같은 형태의 입력을 받으므로
 * 하나의 DTO를 공유한다.
 *
 * <p>D6: <b>{@code customerId}를 두지 않는다.</b> 명세는 세션에서 고객을 꺼내지만,
 * 요청 본문에 아이디를 두면 남의 아이디를 보내 타인의 포인트로 주문할 수 있다.
 * 주문 주체는 항상 인증 정보에서만 얻는다.
 *
 * @param productId 주문할 상품 ID
 * @param quantity  수량. 1 이상
 */
public record OrderRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        // 상한을 두지 않는 이유는 재고(P4)가 실질적인 상한이 되기 때문이다.
        // 지금은 Money.times와 OrderItem.increase의 오버플로 검사가 최후 방어선이다.
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity) {
}
