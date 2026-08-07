package com.sk.skala.shopapi.order.dto;

import java.util.List;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.order.domain.OrderItem;

/**
 * 고객이 주문한 상품 목록 조회 응답.
 *
 * <p>과제 명세 544p의 {@code OrderListDto}에 해당한다.
 * 고객 정보와 주문 목록을 함께 담아, 화면이 두 번 요청하지 않아도 되게 한다.
 *
 * <p>비밀번호는 담지 않는다. 응답 DTO를 따로 두는 이유가 바로 이것이다(D3).
 * 엔티티를 그대로 내보내면 비밀번호 해시를 빼는 처리를 매번 기억해야 하고,
 * 한 번만 빠뜨려도 유출된다. 담을 필드를 명시하면 실수할 여지가 없다.
 *
 * @param customerId 고객 아이디
 * @param point      보유 포인트. 원 단위 정수
 * @param totalSpent 주문 목록의 합계 금액
 * @param products   주문한 상품 목록
 */
public record OrderListResponse(
        String customerId,
        long point,
        long totalSpent,
        List<OrderItemDto> products) {

    public static OrderListResponse of(Customer customer, List<OrderItem> orderItems) {
        List<OrderItemDto> products = orderItems.stream()
                .map(OrderItemDto::from)
                .toList();

        long totalSpent = products.stream()
                .mapToLong(OrderItemDto::totalPrice)
                .sum();

        return new OrderListResponse(
                customer.getCustomerId(),
                customer.getPoint().getAmount(),
                totalSpent,
                products);
    }
}
