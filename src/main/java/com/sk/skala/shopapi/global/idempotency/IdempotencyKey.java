package com.sk.skala.shopapi.global.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    /**
     * 최초 처리 결과를 직렬화한 JSON.
     *
     * <p>처음에는 {@code @Lob}을 붙였다. "길이 제한 없는 문자열"이라는 뜻으로 쓴 것인데,
     * PostgreSQL에서는 그 뜻이 아니었다. Hibernate가 {@code @Lob String}을
     * <b>{@code oid}, 즉 PostgreSQL Large Object로 매핑</b>한다. 값이 행 안에 들어가지 않고
     * {@code pg_largeobject}라는 별도 저장소에 들어가고 컬럼에는 그 번호만 남는다. (D21)
     *
     * <p>그 방식은 여기에 맞지 않는다.
     *
     * <ul>
     *   <li>행을 지워도 Large Object는 남는다. {@code vacuumlo}로 따로 청소해야 한다
     *   <li>autocommit 모드에서는 읽고 쓸 수 없다
     *   <li>일반 SQL로 내용을 조회할 수 없어 운영 중 들여다보기 어렵다
     * </ul>
     *
     * <p>그래서 {@code @Lob}을 떼고 평범한 {@code String}으로 둔다. 길이를 지정하지 않아
     * Hibernate는 varchar 계열로 판단하고, 마이그레이션의 길이 없는 {@code VARCHAR}와 맞는다.
     *
     * <p>H2에서는 이 문제가 드러나지 않았다. H2 방언은 {@code @Lob}을 CLOB으로 매핑하고
     * CLOB은 H2에 실제로 있는 타입이라 아무 경고 없이 통과했다.
     */
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
