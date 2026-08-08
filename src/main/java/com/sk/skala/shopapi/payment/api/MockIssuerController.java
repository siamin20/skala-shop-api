package com.sk.skala.shopapi.payment.api;

import java.time.YearMonth;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.payment.app.PanCipher;
import com.sk.skala.shopapi.payment.dto.AuthorizationRequest;
import com.sk.skala.shopapi.payment.dto.AuthorizationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의 카드사. (D32)
 *
 * <p><b>이것은 우리 서비스가 아니라 카드사 역할을 하는 가짜 서버다.</b>
 * 실제로는 다른 회사의 다른 서버지만, 과제 범위에서 외부 PG와 계약할 수 없어
 * 같은 애플리케이션 안에 두었다.
 *
 * <p>같은 프로세스 안에 있어도 <b>통신은 실제처럼 한다.</b> HTTP로 호출하고
 * 카드번호는 암호화해서 보낸다. 메서드를 직접 부르면 암호화 흐름을 검증할 수 없다.
 *
 * <h2>무엇을 진짜로 하고, 무엇을 흉내만 내는가</h2>
 *
 * <table border="1">
 *   <tr><th></th><th>구현</th></tr>
 *   <tr><td>카드번호 암호화 전송</td><td><b>실제</b> — AES-GCM</td></tr>
 *   <tr><td>복호화와 검증</td><td><b>실제</b> — Luhn, 유효기간</td></tr>
 *   <tr><td>승인/거절 판정</td><td><b>실제 규칙</b> — 카드번호로 결정</td></tr>
 *   <tr><td>은행 간 정산</td><td>없음</td></tr>
 *   <tr><td>3D Secure</td><td>없음</td></tr>
 * </table>
 *
 * <h2>시험용 카드번호</h2>
 *
 * <pre>
 *   4242424242424242   승인
 *   4000000000000002   거절 (한도 초과)
 *   4000000000000069   거절 (유효기간 만료)
 *   그 외 Luhn 통과     승인
 * </pre>
 *
 * <p>실제 PG사가 제공하는 테스트 카드번호와 같은 방식이다. 결과를 카드번호로 정해두면
 * 거절 경로를 언제든 재현할 수 있다. 무작위로 거절하면 테스트가 불안정해진다.
 */
@Tag(name = "모의 카드사", description = "카드사 역할을 하는 가짜 서버. 우리 서비스가 아니다.")
@Slf4j
@RestController
@RequestMapping("/mock-issuer")
@RequiredArgsConstructor
public class MockIssuerController {

    private static final String DECLINE_LIMIT = "4000000000000002";
    private static final String DECLINE_EXPIRED = "4000000000000069";

    private final PanCipher panCipher;

    @Operation(summary = "카드 승인 요청", description = "암호화된 카드 정보를 받아 승인 여부를 판정한다.")
    @PostMapping("/authorize")
    public AuthorizationResponse authorize(@RequestBody AuthorizationRequest request) {
        String decrypted;
        try {
            decrypted = panCipher.decrypt(request.encryptedCard());
        } catch (RuntimeException e) {
            // 복호화 실패는 변조이거나 키 불일치다. 어느 쪽인지 알려주지 않는다.
            log.warn("승인 요청 복호화 실패. orderId={}", request.orderId());
            return AuthorizationResponse.declined("카드 정보를 읽을 수 없습니다");
        }

        String[] parts = decrypted.split("\\|");
        if (parts.length != 3) {
            return AuthorizationResponse.declined("카드 정보 형식이 올바르지 않습니다");
        }

        String pan = parts[0];
        String expiry = parts[1];

        // 로그에는 마스킹된 번호만 남긴다. 카드사라도 평문을 로그에 남기지 않는다.
        log.info("승인 요청 수신. card={}, amount={}, orderId={}",
                mask(pan), request.amount(), request.orderId());

        if (!passesLuhn(pan)) {
            return AuthorizationResponse.declined("유효하지 않은 카드번호입니다");
        }
        if (isExpired(expiry) || DECLINE_EXPIRED.equals(pan)) {
            return AuthorizationResponse.declined("유효기간이 지난 카드입니다");
        }
        if (DECLINE_LIMIT.equals(pan)) {
            return AuthorizationResponse.declined("한도를 초과했습니다");
        }
        if (request.amount() <= 0) {
            return AuthorizationResponse.declined("승인 금액이 올바르지 않습니다");
        }

        String approvalNumber = "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
        log.info("승인 완료. approval={}, card={}", approvalNumber, mask(pan));

        return AuthorizationResponse.approved(approvalNumber, mask(pan));
    }

    @Operation(summary = "승인 취소", description = "승인 번호로 원거래를 취소한다.")
    @PostMapping("/cancel")
    public AuthorizationResponse cancel(@RequestBody AuthorizationRequest request) {
        // 원거래 조회 없이 항상 성공으로 다룬다. 실제 카드사는 승인 원장을 갖고 있지만
        // 그 원장을 흉내 내는 것은 이 모의 서버의 목적이 아니다.
        log.info("승인 취소. orderId={}", request.orderId());
        return AuthorizationResponse.approved("CANCELLED", "****");
    }

    /**
     * Luhn 검사.
     *
     * <p>카드번호에 들어 있는 체크섬이다. 오타를 걸러내려고 만든 것이라
     * <b>보안 장치가 아니다.</b> 통과했다고 실제 존재하는 카드인 것도 아니다.
     * 그래도 검사하는 이유는 명백한 오타를 카드사까지 보내지 않기 위해서다.
     */
    private boolean passesLuhn(String pan) {
        if (!pan.matches("\\d{13,19}")) return false;

        int sum = 0;
        boolean doubling = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int digit = pan.charAt(i) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }

    private boolean isExpired(String expiry) {
        try {
            YearMonth valid = YearMonth.parse("20" + expiry.substring(3) + "-" + expiry.substring(0, 2));
            // 유효기간은 그 달의 말일까지다. 같은 달이면 아직 살아 있다.
            return valid.isBefore(YearMonth.now());
        } catch (RuntimeException e) {
            return true;
        }
    }

    private String mask(String pan) {
        return pan.length() < 4 ? "****" : "**** **** **** " + pan.substring(pan.length() - 4);
    }
}
