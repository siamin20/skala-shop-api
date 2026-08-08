package com.sk.skala.shopapi.order.dto;

import java.util.List;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.global.common.Money;
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

    /**
     * 고객과 주문 목록을 하나의 응답으로 묶는다.
     *
     * <p>둘을 한 응답에 담는 이유는 주문 내역 화면이 잔액과 목록을 함께 보여주기 때문이다.
     * 나눠 두면 화면 하나를 그리는 데 요청이 두 번 나가고, 그 사이에 주문이 일어나면
     * 잔액과 목록이 서로 다른 시점을 가리킨다.
     *
     * <p>합계는 {@code long}이 아니라 {@link Money}로 누적한다. 금액 규칙(음수 불가, 오버플로 검사)을
     * 도메인 타입 안에 두기 위해서다. 원시 타입으로 더하면 그 검사를 우회하게 되고,
     * 응답을 만드는 이 지점에서만 조용히 규칙 밖으로 나가는 값이 생긴다.
     */
    public static OrderListResponse of(Customer customer, List<OrderItem> orderItems) {
        List<OrderItemDto> products = orderItems.stream()
                .map(OrderItemDto::from)
                .toList();

        Money totalSpent = orderItems.stream()
                .map(OrderItem::totalPrice)
                .reduce(Money.ZERO, Money::plus);

        return new OrderListResponse(
                customer.getCustomerId(),
                customer.getPoint().getAmount(),
                totalSpent.getAmount(),
                products);
    }
}
