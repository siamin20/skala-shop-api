package com.sk.skala.shopapi.global.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 명세 536p의 {@code POST /api/customers/order}, {@code /cancel}용 요청. (D27)
 *
 * <p><b>{@code customerId}를 받지 않는다.</b> 명세는 주문 주체를 세션에서 꺼내고,
 * 이 프로젝트는 그 자리를 JWT가 대신한다. 본문으로 받으면 남의 아이디를 보내
 * 타인의 포인트로 주문할 수 있다. 필드가 없으면 실수로 그 값을 쓸 수가 없다. (D6)
 */
public record SpecOrderRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity) {
}
