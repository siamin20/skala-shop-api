package com.sk.skala.shopapi.payment.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 클라이언트가 보내는 카드 정보. (D32)
 *
 * <p><b>이 값은 저장되지 않는다.</b> 승인 요청을 만드는 데만 쓰고 버린다.
 * 저장하려면 PCI DSS 준수가 필요하고, 그 부담을 지지 않는 가장 확실한 방법은
 * 애초에 갖고 있지 않는 것이다.
 *
 * <p>{@code toString}을 그대로 두지 않는다. 레코드의 기본 구현은 모든 필드를 찍기 때문에
 * 로그 한 줄에 카드번호가 통째로 남을 수 있다.
 */
public record CardPaymentRequest(

        @Pattern(regexp = "\\d{13,19}", message = "카드번호는 숫자 13~19자리입니다")
        String cardNumber,

        @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "유효기간은 MM/YY 형식입니다")
        String expiry,

        @Pattern(regexp = "\\d{3,4}", message = "CVC는 숫자 3~4자리입니다")
        String cvc) {

    /**
     * 뒷자리만 남긴 표기.
     *
     * <p>영수증과 주문 내역에 쓴다. 사용자는 어느 카드로 결제했는지 알아야 하고,
     * 뒷 네 자리면 충분하다.
     */
    public String masked() {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * 로그에 카드번호가 새지 않게 막는다.
     *
     * <p>이 메서드가 없으면 요청 객체를 무심코 로그에 넣는 순간 카드번호가 남는다.
     * 예외 메시지에 객체가 딸려 들어가는 경우가 특히 흔하다.
     */
    @Override
    public String toString() {
        return "CardPaymentRequest[card=%s, expiry=%s, cvc=***]".formatted(masked(), expiry);
    }
}
