package com.sk.skala.shopapi.payment.app;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 설정. (D32)
 *
 * @param encryptionKey 카드번호 암호화 키. AES-256을 쓰므로 32바이트 이상이어야 한다
 * @param issuerUrl     모의 카드사 주소
 */
@ConfigurationProperties(prefix = "shop.payment")
public record PaymentProperties(String encryptionKey, String issuerUrl) {

    private static final int MIN_KEY_BYTES = 32;

    public PaymentProperties {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("""
                    PAYMENT_ENCRYPTION_KEY가 설정되지 않았습니다.

                    32바이트 이상의 임의 문자열을 지정하세요.
                      export PAYMENT_ENCRYPTION_KEY="$(openssl rand -base64 48)"

                    JWT 시크릿과 같은 이유로 기본값을 두지 않습니다(D26).
                    저장소에 커밋된 키로 암호화하면 암호화하지 않은 것과 같습니다.""");
        }
        // 바이트로 센다. 글자 수로 세면 한글 키가 짧아도 통과한다. (D14, D26)
        int length = encryptionKey.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "결제 암호화 키가 너무 짧습니다. %d바이트인데 최소 %d바이트가 필요합니다"
                            .formatted(length, MIN_KEY_BYTES));
        }
        if (issuerUrl == null || issuerUrl.isBlank()) {
            issuerUrl = "http://localhost:8080/mock-issuer";
        }
    }
}
