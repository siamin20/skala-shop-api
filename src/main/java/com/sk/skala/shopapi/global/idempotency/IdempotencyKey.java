package com.sk.skala.shopapi.global.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 처리 완료된 요청의 기록.
 *
 * <p>D20: 같은 키로 다시 들어온 요청은 작업을 실행하지 않고 저장된 응답을 그대로 돌려준다.
 *
 * <p>키를 기본 키로 둔 것이 핵심이다. 애플리케이션에서 "이미 있는가"만 확인하면
 * 동시 요청 둘이 각자 "없음"을 확인하고 둘 다 실행할 수 있다.
 * <b>기본 키 제약이 최종 방어선</b>이고, 애플리케이션 검사는 좋은 응답을 주기 위한 것이다.
 */
@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String key;

    /**
     * 키를 발급받아 쓴 고객.
     *
     * <p>남이 쓴 키를 재사용해 그 응답을 들여다보는 것을 막는다.
     * 키는 클라이언트가 정하므로 추측 가능한 값이 올 수도 있다.
     */
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    /**
     * 요청 내용의 지문.
     *
     * <p>같은 키로 다른 요청을 보내면 거부하기 위해 둔다. 없으면 "5,000원 충전"에 쓴 키로
     * "50,000원 충전"을 보내 <b>실행되지 않은 채 성공 응답만 받아낼</b> 수 있다.
     */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    /** 최초 처리 결과를 직렬화한 JSON. */
    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyKey(String key, String customerId, String requestFingerprint,
            String responseBody, Instant expiresAt) {
        this.key = key;
        this.customerId = customerId;
        this.requestFingerprint = requestFingerprint;
        this.responseBody = responseBody;
        this.expiresAt = expiresAt;
    }

    /** 이 기록을 만든 고객이 맞는지. */
    public boolean belongsTo(String customerId) {
        return this.customerId.equals(customerId);
    }

    /** 같은 내용의 요청인지. */
    public boolean matches(String requestFingerprint) {
        return this.requestFingerprint.equals(requestFingerprint);
    }
}
