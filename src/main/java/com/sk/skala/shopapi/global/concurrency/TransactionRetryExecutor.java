package com.sk.skala.shopapi.global.concurrency;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 낙관적 락 충돌을 재시도하는 계층. (D22)
 *
 * <h2>왜 트랜잭션 바깥에 있어야 하는가</h2>
 *
 * <p>이 클래스에 {@code @Transactional}이 없는 것이 핵심이다.
 *
 * <p>낙관적 락 충돌은 <b>커밋 시점</b>에 드러난다. Hibernate가
 * {@code UPDATE ... WHERE version = ?}를 날렸는데 바뀐 행이 0이면 그때 예외가 난다.
 * 그 시점의 트랜잭션은 이미 롤백 표시가 붙어 있어서 <b>안에서 무엇을 다시 시도해도
 * 커밋되지 않는다.</b> 재시도하려면 트랜잭션이 끝난 뒤 새 트랜잭션을 열어야 한다.
 *
 * <pre>
 *   IdempotentExecutor        트랜잭션 없음
 *     └ TransactionRetryExecutor   트랜잭션 없음  ← 여기
 *          └ IdempotencyStore      @Transactional  ← 시도마다 새 트랜잭션
 *               └ OrderService     위 트랜잭션에 참여
 * </pre>
 *
 * <p>같은 이유로 {@code IdempotentExecutor}도 트랜잭션 바깥에 있다.
 * 기본 키 위반을 트랜잭션 안에서 잡아봐야 이미 롤백 표시이기 때문이다.
 *
 * <h2>재시도 순서가 멱등성보다 안쪽인 이유</h2>
 *
 * <p>재시도가 멱등성 <b>안쪽</b>에 있다. 충돌로 트랜잭션이 롤백되면 그 안에서 저장하던
 * 멱등성 키도 함께 사라지므로, 다음 시도는 "처음 보는 키"로 다시 실행된다. 의도한 대로다.
 *
 * <p>반대로 두면 각 재시도가 저장된 응답을 먼저 찾게 되는데, 애초에 저장에 실패해서
 * 재시도하는 상황이라 매번 못 찾는다. 결과는 같지만 키 조회가 헛돈다.
 *
 * <h2>무엇을 재시도하고 무엇을 재시도하지 않는가</h2>
 *
 * <table border="1">
 *   <tr><th>상황</th><th>처리</th><th>이유</th></tr>
 *   <tr>
 *     <td>낙관적 락 충돌</td><td>재시도</td>
 *     <td>다시 읽으면 성공할 가능성이 높다. 경합이 드물다는 전제로 고른 전략이다</td>
 *   </tr>
 *   <tr>
 *     <td>비관적 락 타임아웃</td><td>재시도하지 않고 {@code LOCK_TIMEOUT}</td>
 *     <td>이미 기다려본 뒤 실패한 것이다. 곧바로 또 기다리면 대기 시간만 배로 늘고
 *         경합이 심할수록 서버가 스스로 부하를 키운다</td>
 *   </tr>
 *   <tr>
 *     <td>업무 규칙 위반 (잔액 부족 등)</td><td>재시도하지 않고 그대로 전파</td>
 *     <td>다시 해도 같은 결과다</td>
 *   </tr>
 * </table>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionRetryExecutor {

    private final ConcurrencyProperties properties;
    private final RetryMetrics metrics;

    /**
     * 충돌하면 새 트랜잭션으로 다시 시도한다.
     *
     * @param operation 트랜잭션 경계를 여는 호출. 시도마다 새로 실행된다
     * @throws BusinessException 시도를 모두 소진하면 {@code CONCURRENT_MODIFICATION},
     *                           락 대기가 만료되면 {@code LOCK_TIMEOUT}
     */
    public <T> T execute(Supplier<T> operation) {
        int maxAttempts = properties.maxRetryAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.get();
                metrics.recordSuccess(attempt);
                return result;

            } catch (OptimisticLockingFailureException e) {
                metrics.recordConflict();

                if (attempt == maxAttempts) {
                    log.warn("낙관적 락 재시도 {}회를 모두 소진했다", maxAttempts);
                    metrics.recordExhausted();
                    throw new BusinessException(
                            ErrorCode.CONCURRENT_MODIFICATION,
                            "다른 요청과 충돌해 처리하지 못했습니다. 잠시 후 다시 시도해 주세요");
                }

                backOff(attempt);

            } catch (PessimisticLockingFailureException e) {
                // 비관적 락 타임아웃이다. 이미 기다려본 뒤 실패한 것이라 다시 기다리지 않는다.
                //
                // CannotAcquireLockException은 PessimisticLockingFailureException의
                // 하위 타입이라 multi-catch로 함께 적을 수 없다. 상위 타입 하나로 둘 다 잡는다.
                metrics.recordLockTimeout();
                throw new BusinessException(
                        ErrorCode.LOCK_TIMEOUT,
                        "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요");
            }
        }

        // 반복문 구조상 도달할 수 없다. 컴파일러를 위한 문장이다.
        throw new IllegalStateException("재시도 루프를 벗어났다");
    }

    /**
     * 다음 시도까지 기다린다. <b>지수 백오프 + 지터</b>를 쓴다.
     *
     * <p>고정 간격으로 기다리면 충돌했던 요청들이 <b>같은 시각에 동시에 깨어나</b>
     * 그대로 다시 충돌한다. 이를 thundering herd라고 부른다.
     * 대기 시간에 무작위 폭을 섞으면 깨어나는 시점이 흩어져 두 번째 충돌이 줄어든다.
     *
     * <p>대기 총합은 트랜잭션 타임아웃 안에 들어와야 한다. 그렇지 않으면 재시도를
     * 다 쓰기 전에 타임아웃이 먼저 터져 재시도 설정이 무의미해진다.
     * 계산은 {@link ConcurrencyProperties}의 문서 주석에 있다.
     */
    private void backOff(int attempt) {
        long base = properties.retryBackoff().toMillis() * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(base + 1);

        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 상위 계층이 종료 신호를 놓친다.
            // 플래그를 되살리고 재시도를 포기한다.
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION, "요청이 중단되었습니다");
        }
    }
}
