package com.sk.skala.shopapi.order.dto;

import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.payment.dto.CardPaymentRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 결제까지 포함한 주문 요청. (D31, D32)
 *
 * <p>{@link OrderRequest}를 대체하지 않고 <b>따로 둔다.</b> 명세 경로
 * ({@code POST /api/customers/order})는 결제 수단이라는 개념 자체가 없으므로
 * 기존 요청 모양을 그대로 유지해야 한다. 필드를 늘리면 명세대로 호출한 요청이
 * 검증에 걸릴 수 있다.
 *
 * <p>{@code customerId}는 여기에도 없다. 주문 주체는 토큰에서만 온다. (D6)
 *
 * @param productId     상품
 * @param quantity      수량
 * @param paymentMethod 결제 수단. 생략하면 {@code POINT}(명세 기본)
 * @param usePoint      포인트로 낼 금액. 카드 결제일 때만 의미가 있다
 * @param card          카드 정보. 카드 결제일 때만 필요하며 저장되지 않는다
 */
public record CheckoutRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity,

        PaymentMethod paymentMethod,

        @PositiveOrZero(message = "사용 포인트는 0 이상이어야 합니다")
        Long usePoint,

        @Valid
        CardPaymentRequest card) {

    public CheckoutRequest {
        // 생략하면 명세의 기본 동작이다. 명세대로 보낸 요청이 그대로 통해야 한다.
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.POINT;
        }
        if (usePoint == null) {
            usePoint = 0L;
        }
    }

    public OrderRequest toOrderRequest() {
        return new OrderRequest(productId, quantity);
    }
}
