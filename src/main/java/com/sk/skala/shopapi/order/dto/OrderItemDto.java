package com.sk.skala.shopapi.order.dto;

import com.sk.skala.shopapi.order.domain.OrderItem;

/**
 * 고객이 주문한 상품 한 건.
 *
 * <p>과제 명세 543p의 {@code OrderItemDto}에 해당한다.
 * 명세는 Builder 패턴과 Setter를 요구하지만 {@code record}로 만들었다.
 * 응답 DTO는 만들어진 뒤 값이 바뀔 일이 없고, {@code record}의 정적 팩터리
 * {@link #from(OrderItem)} 하나면 Builder가 하던 역할을 대신하기 때문이다.
 *
 * <p>{@code price}는 <b>주문 시점 단가</b>다. 현재 상품 가격이 아니다.
 * 목록에 지금 가격을 보여주면, 취소했을 때 돌려받는 금액과 화면의 숫자가 어긋난다.
 *
 * @param productId   상품 ID
 * @param productName 상품명
 * @param price       주문 시점 단가. 원 단위 정수 (D1)
 * @param quantity    주문 수량
 * @param totalPrice  단가 × 수량
 */
public record OrderItemDto(
        Long productId,
        String productName,
        long price,
        int quantity,
        long totalPrice) {

    /**
     * 주문 항목을 응답으로 옮긴다.
     *
     * <p>단가를 상품의 현재 가격이 아니라 주문 항목의 스냅샷에서 가져온다.
     * 화면에 지금 가격을 보여주면, 취소했을 때 실제로 돌려받는 금액과 숫자가 어긋나
     * 사용자가 환불 오류로 오해한다.
     *
     * <p>합계도 여기서 계산해 담는다. 클라이언트가 단가 × 수량을 다시 계산하게 하면
     * 반올림이나 할인 규칙이 생겼을 때 서버와 화면의 값이 갈라진다.
     */
    public static OrderItemDto from(OrderItem orderItem) {
        return new OrderItemDto(
                orderItem.getProduct().getId(),
                orderItem.getProduct().getName(),
                orderItem.getUnitPrice().getAmount(),
                orderItem.getQuantity(),
                orderItem.totalPrice().getAmount());
    }
}
