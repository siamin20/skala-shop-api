package com.sk.skala.shopapi.global.concurrency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 동시성 제어 설정. (D22)
 *
 * <h2>계층 순서 제약</h2>
 *
 * <p>타임아웃은 <b>안쪽일수록 짧아야 한다.</b> 순서가 뒤집히면 방어선이 무력화된다.
 *
 * <pre>
 *   락 획득  &lt;  재시도 총합  &lt;  트랜잭션  &lt;  문장  &lt;  커넥션 획득
 *     3s          750ms          5s          10s         30s
 * </pre>
 *
 * <p>락 타임아웃이 커넥션 타임아웃보다 길면, 락을 기다리는 동안 커넥션 풀이 먼저 고갈된다.
 * 그러면 락 타임아웃은 한 번도 발동하지 못하고 <b>락과 무관한 API까지 함께 멈춘다.</b>
 * 국소적인 경합이 전역 장애로 번지는 경로다.
 *
 * <h2>재시도 총합 계산</h2>
 *
 * <p>지터 때문에 실제 대기는 매번 다르지만 <b>최악의 경우</b>로 잡아야 한다.
 * 백오프 50ms, 5회 시도면 대기는 4번 일어난다.
 *
 * <pre>
 *   1회 실패 후: 최대  50ms  (2^0 × 50)
 *   2회 실패 후: 최대 100ms  (2^1 × 50)
 *   3회 실패 후: 최대 200ms  (2^2 × 50)
 *   4회 실패 후: 최대 400ms  (2^3 × 50)
 *   ────────────────────────────────
 *   대기 총합 최대 750ms + 작업 5회 실행 시간
 * </pre>
 *
 * <p>트랜잭션 타임아웃 5초 안에 들어온다. 백오프를 키우거나 시도를 늘릴 때는
 * 이 계산을 다시 해야 한다. 시도를 10회로 늘리면 대기 총합만 24초가 되어
 * <b>트랜잭션 타임아웃보다 길어진다.</b> 그러면 재시도를 다 쓰기 전에 타임아웃이
 * 먼저 터져 설정이 무의미해진다.
 *
 * @param maxRetryAttempts 낙관적 락 충돌 시 총 시도 횟수 (첫 시도 포함)
 * @param retryBackoff     첫 재시도까지의 기준 대기 시간. 시도마다 2배가 되고 지터가 섞인다
 * @param lockTimeout      비관적 락 대기 한도. PostgreSQL {@code lock_timeout}으로 적용된다
 */
@ConfigurationProperties(prefix = "shop.concurrency")
public record ConcurrencyProperties(
        int maxRetryAttempts,
        Duration retryBackoff,
        Duration lockTimeout) {

    public ConcurrencyProperties {
        if (maxRetryAttempts < 1) {
            throw new IllegalArgumentException("시도 횟수는 1 이상이어야 합니다");
        }
    }
}
