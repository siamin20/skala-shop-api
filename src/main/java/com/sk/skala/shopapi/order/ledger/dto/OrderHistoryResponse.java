package com.sk.skala.shopapi.order.ledger.dto;

import java.time.Instant;
import java.util.List;

import com.sk.skala.shopapi.order.ledger.Order;

/**
 * 주문 내역 응답. (D43)
 *
 * <p>{@code GET /api/customers/{id}}가 반환하는 것과 다르다. 그쪽은 <b>지금 보유한 것</b>이고
 * 이쪽은 <b>무슨 일이 있었나</b>다. 취소한 주문도 여기에는 남는다.
 */
public record OrderHistoryResponse(
        String orderNo,
        String status,
        long totalAmount,
        long pointAmount,
        long cardAmount,
        long earnedPoint,
        String recipient,
        String address,
        Instant orderedAt,
        Instant cancelledAt,
        List<Line> lines) {

    public record Line(
            Long productId,
            String productName,
            long unitPrice,
            int quantity,
            int cancelledQuantity,
            long lineAmount) {
    }

    public static OrderHistoryResponse from(Order order) {
        return new OrderHistoryResponse(
                order.getOrderNo(),
                order.getStatus().name(),
                order.getTotalAmount().getAmount(),
                order.getPointAmount().getAmount(),
                order.getCardAmount().getAmount(),
                order.getEarnedPoint().getAmount(),
                order.getRecipient(),
                order.fullAddress(),
                order.getOrderedAt(),
                order.getCancelledAt(),
                order.getLines().stream()
                        .map(l -> new Line(
                                l.getProductId(), l.getProductName(),
                                l.getUnitPrice().getAmount(), l.getQuantity(),
                                l.getCancelledQuantity(), l.getLineAmount().getAmount()))
                        .toList());
    }
}
