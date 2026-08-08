package com.sk.skala.shopapi.payment.dto;

/**
 * 카드사로 보내는 승인 요청. (D32)
 *
 * <p>카드번호는 <b>암호화된 상태로만</b> 담긴다. 평문 필드가 아예 없어서
 * 실수로 평문을 실을 수가 없다.
 *
 * @param encryptedCard 암호화한 "카드번호|유효기간|CVC"
 * @param amount        승인 요청 금액(원)
 * @param orderId       가맹점 주문 번호. 중복 승인을 막는 데 쓴다
 */
public record AuthorizationRequest(String encryptedCard, long amount, String orderId) {
}
