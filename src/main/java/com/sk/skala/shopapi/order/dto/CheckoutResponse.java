package com.sk.skala.shopapi.order.dto;

/**
 * 결제 결과. 영수증에 해당한다. (D31, D32)
 *
 * @param orders         결제 후 주문 내역과 잔여 포인트
 * @param paidByPoint    포인트로 낸 금액
 * @param paidByCard     카드로 낸 금액
 * @param earnedPoint    이번 결제로 적립된 포인트
 * @param approvalNumber 카드 승인 번호. 포인트 전액 결제면 null
 * @param maskedCard     마스킹된 카드번호. 포인트 전액 결제면 null
 */
public record CheckoutResponse(
        OrderListResponse orders,
        long paidByPoint,
        long paidByCard,
        long earnedPoint,
        String approvalNumber,
        String maskedCard) {
}
