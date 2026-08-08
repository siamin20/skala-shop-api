package com.sk.skala.shopapi.global.concurrency;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 재시도 계층에서 무슨 일이 있었는지 세는 카운터. (D22)
 *
 * <p>동시성은 <b>결과만 보면 잘 동작하는 것처럼 보인다.</b> 재고가 정확히 0이 되었다는
 * 사실만으로는 그 과정에서 충돌이 한 번도 없었는지, 재시도가 스무 번 돌았는지 알 수 없다.
 * 두 경우는 처리량이 전혀 다른데 겉으로는 구분되지 않는다.
 *
 * <p>그래서 과정을 센다. 로드맵이 P4의 완료 조건으로 요구하는 "측정치"가 이것이다.
 * 추정한 숫자를 문서에 적지 않기 위해서다.
 *
 * <p>Micrometer로 내보내는 것은 P5에서 다룬다. 지금은 테스트가 읽을 수 있으면 충분하고,
 * 관측 파이프라인이 없는 상태에서 계측 라이브러리만 먼저 넣으면 확인할 방법이 없다.
 *
 * <p>애플리케이션 전역 카운터라 테스트는 시작 전에 {@link #reset()}을 불러야 한다.
 */
@Component
public class RetryMetrics {

    private final AtomicLong conflicts = new AtomicLong();
    private final AtomicLong exhausted = new AtomicLong();
    private final AtomicLong lockTimeouts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();

    /** 첫 시도에 성공하지 못한 횟수의 합. 재시도로 이어진 충돌 건수다. */
    private final AtomicLong retriedAttempts = new AtomicLong();

    void recordConflict() {
        conflicts.incrementAndGet();
    }

    void recordExhausted() {
        exhausted.incrementAndGet();
    }

    void recordLockTimeout() {
        lockTimeouts.incrementAndGet();
    }

    void recordSuccess(int attempt) {
        successes.incrementAndGet();
        // attempt가 1이면 재시도 없이 통과한 것이다.
        retriedAttempts.addAndGet(attempt - 1L);
    }

    /** 낙관적 락 충돌 횟수. 재시도해서 결국 성공한 경우도 포함한다. */
    public long conflicts() {
        return conflicts.get();
    }

    /** 재시도를 모두 소진해 {@code CONCURRENT_MODIFICATION}으로 끝난 횟수. */
    public long exhausted() {
        return exhausted.get();
    }

    /** 비관적 락 대기가 만료된 횟수. */
    public long lockTimeouts() {
        return lockTimeouts.get();
    }

    /** 최종적으로 성공한 요청 수. */
    public long successes() {
        return successes.get();
    }

    /** 성공한 요청들이 쓴 재시도 횟수의 합. 0이면 아무도 다시 시도하지 않았다는 뜻이다. */
    public long retriedAttempts() {
        return retriedAttempts.get();
    }

    public void reset() {
        conflicts.set(0);
        exhausted.set(0);
        lockTimeouts.set(0);
        successes.set(0);
        retriedAttempts.set(0);
    }
}
