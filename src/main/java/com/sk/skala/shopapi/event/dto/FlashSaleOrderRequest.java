package com.sk.skala.shopapi.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 선착순 이벤트 참여 요청. (D23)
 *
 * <p>{@code customerId}가 없다. 주문과 같은 이유로 참여 주체는 토큰에서만 온다. (D6)
 * 필드가 없으면 실수로 그 값을 신뢰할 수가 없다.
 *
 * <p>전략을 고르는 필드도 없다. 어떤 방식으로 수량을 지키는지는 서버의 운영 결정이지
 * 클라이언트가 요청마다 바꿀 수 있는 값이 아니다.
 */
public record FlashSaleOrderRequest(

        @NotNull(message = "이벤트 ID는 필수입니다")
        Long flashSaleId,

        // 선착순은 1인당 소량이 보통이지만 상한은 이벤트 수량이 실질적으로 정한다.
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity) {
}
