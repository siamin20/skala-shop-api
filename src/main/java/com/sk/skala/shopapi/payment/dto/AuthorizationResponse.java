package com.sk.skala.shopapi.payment.dto;

/**
 * 카드사의 승인 응답. (D32)
 *
 * @param approved       승인 여부
 * @param approvalNumber 승인 번호. 취소할 때 이 번호로 원거래를 지정한다
 * @param maskedCard     카드사가 돌려주는 마스킹된 번호
 * @param declineReason  거절 사유. 승인이면 null
 */
public record AuthorizationResponse(
        boolean approved,
        String approvalNumber,
        String maskedCard,
        String declineReason) {

    public static AuthorizationResponse approved(String approvalNumber, String maskedCard) {
        return new AuthorizationResponse(true, approvalNumber, maskedCard, null);
    }

    public static AuthorizationResponse declined(String reason) {
        return new AuthorizationResponse(false, null, null, reason);
    }
}
