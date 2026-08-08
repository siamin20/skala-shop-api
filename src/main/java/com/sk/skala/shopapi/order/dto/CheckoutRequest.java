package com.sk.skala.shopapi.order.dto;

import java.util.List;

import com.sk.skala.shopapi.delivery.dto.DeliveryAddressRequest;
import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.payment.dto.CardPaymentRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 장바구니 결제 요청. (D31, D32, D34, D37)
 *
 * <p>{@link OrderRequest}를 대체하지 않고 따로 둔다. 명세 경로
 * ({@code POST /api/customers/order})는 결제 수단도 장바구니도 없으므로
 * 기존 요청 모양을 그대로 유지해야 한다.
 *
 * <h2>왜 여러 상품을 한 번에 받는가</h2>
 *
 * <p>장바구니에 담은 것을 상품마다 따로 결제하면 <b>카드 승인이 상품 수만큼 일어난다.</b>
 * 사용자는 명세서에 결제 내역이 다섯 줄 찍히는 것을 보게 되고, 중간에 하나가 거절되면
 * 앞의 것만 결제된 어중간한 상태가 남는다.
 *
 * <p>한 번에 받아 합계로 승인하면 그런 일이 없다. 하나라도 실패하면 전부 되돌린다.
 *
 * @param items         장바구니 항목
 * @param paymentMethod 결제 수단. 생략하면 {@code POINT}
 * @param usePoint      적립금 사용액. 카드 결제일 때만 의미가 있다
 * @param card          카드 정보. 저장되지 않는다
 * @param delivery      배송지. 함께 보내면 기본 배송지로 저장된다
 */
public record CheckoutRequest(

        @NotEmpty(message = "주문할 상품이 없습니다")
        @Valid
        List<Item> items,

        PaymentMethod paymentMethod,

        @PositiveOrZero(message = "사용 적립금은 0 이상이어야 합니다")
        Long usePoint,

        @Valid
        CardPaymentRequest card,

        @Valid
        DeliveryAddressRequest delivery) {

    public CheckoutRequest {
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.POINT;
        }
        if (usePoint == null) {
            usePoint = 0L;
        }
    }

    /** 장바구니 한 줄. */
    public record Item(
            @NotNull(message = "상품 ID는 필수입니다")
            Long productId,

            @NotNull(message = "수량은 필수입니다")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
            Integer quantity) {

        public OrderRequest toOrderRequest() {
            return new OrderRequest(productId, quantity);
        }
    }
}
