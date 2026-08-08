package com.sk.skala.shopapi.payment.app;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.payment.dto.AuthorizationRequest;
import com.sk.skala.shopapi.payment.dto.AuthorizationResponse;
import com.sk.skala.shopapi.payment.dto.CardPaymentRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * 카드사와 통신하는 쪽. 우리 서비스가 카드사를 부르는 창구다. (D32)
 *
 * <h2>같은 프로세스 안에 있어도 HTTP로 부른다</h2>
 *
 * <p>모의 카드사가 같은 애플리케이션에 있으므로 메서드를 직접 부르면 훨씬 빠르다.
 * 그런데도 HTTP로 부르는 이유는, <b>그래야 암호화·직렬화·타임아웃이 실제로 동작하는지
 * 검증되기 때문이다.</b> 직접 부르면 암호화 코드는 있지만 아무것도 지키지 않는 장식이 된다.
 *
 * <h2>트랜잭션 안에서 부르지 않는다</h2>
 *
 * <p>외부 호출은 언제 끝날지 모른다. DB 트랜잭션 안에서 부르면 <b>응답을 기다리는 동안
 * 커넥션과 락을 붙잡고 있다.</b> 카드사가 5초 늦으면 그 5초 동안 상품 행이 잠겨
 * 다른 주문이 전부 막힌다. D22에서 다룬 커넥션 고갈이 여기서도 그대로 일어난다.
 *
 * <p>그래서 <b>승인을 먼저 받고, 그 결과를 들고 트랜잭션에 들어간다.</b>
 * 순서를 이렇게 두면 트랜잭션 구간에 외부 호출이 없다.
 *
 * <h2>승인 후 DB가 실패하면</h2>
 *
 * <p>돈은 빠져나갔는데 주문은 없는 상태가 된다. 가장 나쁜 경우다.
 * 그래서 주문 저장이 실패하면 <b>승인을 취소한다.</b> 취소마저 실패하면 로그로 남긴다.
 * 자동으로 해결할 수 없는 종류라 사람이 봐야 한다.
 */
@Slf4j
@Component
public class CardPaymentGateway {

    private final RestClient restClient;
    private final PanCipher panCipher;

    public CardPaymentGateway(PanCipher panCipher, PaymentProperties properties) {
        this.panCipher = panCipher;
        this.restClient = RestClient.builder().baseUrl(properties.issuerUrl()).build();
    }

    /**
     * 승인을 요청한다.
     *
     * @throws BusinessException 거절되면 {@code PAYMENT_DECLINED}
     */
    public Approval authorize(CardPaymentRequest card, Money amount, String orderId) {
        // 카드번호·유효기간·CVC를 하나로 묶어 통째로 암호화한다.
        // 필드를 따로 암호화하면 각 조각의 길이가 드러나고, 조각을 바꿔치기할 여지도 생긴다.
        String encrypted = panCipher.encrypt(
                "%s|%s|%s".formatted(card.cardNumber(), card.expiry(), card.cvc()));

        AuthorizationResponse response;
        try {
            response = restClient.post()
                    .uri("/authorize")
                    .body(new AuthorizationRequest(encrypted, amount.getAmount(), orderId))
                    .retrieve()
                    .body(AuthorizationResponse.class);

        } catch (RuntimeException e) {
            // 카드사에 닿지 못했다. 승인이 됐는지 안 됐는지 알 수 없는 상태다.
            // 성공으로 다루면 공짜로 팔리고, 실패로 다루면 이중 청구 위험이 남는다.
            // 여기서는 실패로 보되, 실제 서비스라면 승인 조회로 확인해야 한다.
            log.error("카드사 통신 실패. orderId={}", orderId, e);
            throw new BusinessException(ErrorCode.PAYMENT_FAILED,
                    "결제 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요");
        }

        if (response == null || !response.approved()) {
            String reason = response == null ? "응답 없음" : response.declineReason();
            log.info("승인 거절. orderId={}, reason={}", orderId, reason);
            throw new BusinessException(ErrorCode.PAYMENT_DECLINED, reason);
        }

        return new Approval(response.approvalNumber(), response.maskedCard());
    }

    /**
     * 승인을 취소한다.
     *
     * <p>주문 저장이 실패했을 때 되돌리는 보상 동작이다. <b>여기서 예외를 던지면 안 된다.</b>
     * 이미 실패 처리 중인데 또 예외가 나면 원래 실패 원인이 가려진다.
     */
    public void cancelQuietly(String approvalNumber, String orderId) {
        try {
            restClient.post()
                    .uri("/cancel")
                    .body(new AuthorizationRequest(approvalNumber, 0, orderId))
                    .retrieve()
                    .body(AuthorizationResponse.class);

        } catch (RuntimeException e) {
            // 돈은 빠져나갔는데 주문이 없는 상태다. 자동으로 해결할 수 없다.
            log.error("승인 취소 실패. 수동 확인이 필요하다. approval={}, orderId={}",
                    approvalNumber, orderId, e);
        }
    }

    /** 승인 결과. 카드번호는 마스킹된 형태로만 넘어온다. */
    public record Approval(String approvalNumber, String maskedCard) {
    }
}
