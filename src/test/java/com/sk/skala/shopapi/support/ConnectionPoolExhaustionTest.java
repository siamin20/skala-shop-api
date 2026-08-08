package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 락 타임아웃이 없으면 무관한 API까지 함께 멈추는지 재현한다. (D22, D44)
 *
 * <h2>왜 이 테스트를 만들었나</h2>
 *
 * <p>설계 문서에 이 장애 연쇄를 적어뒀다.
 *
 * <pre>
 * 인기 상품 한 행에 요청이 몰림 → 락 대기가 길어짐
 *   → 대기하는 동안 커넥션을 계속 붙잡음 → 커넥션 풀 고갈
 *   → 락과 무관한 API(상품 조회, 로그인)까지 커넥션을 못 얻음 → 전면 장애
 * </pre>
 *
 * <p>그런데 <b>확인한 것은 "1초 설정에서 1031ms에 끊긴다"까지였다.</b>
 * 끊긴다는 사실은 쟀지만 <b>끊지 않으면 무슨 일이 벌어지는지는 재보지 않았다.</b>
 * 문서에 근거 없이 적혀 있던 셈이라, 재현해서 수치로 바꾼다.
 *
 * <h2>왜 애플리케이션이 아니라 JDBC 수준에서 재현하는가</h2>
 *
 * <p>{@code lock_timeout}은 데이터소스 단위 설정이라, 켠 상태와 끈 상태를 나란히 비교하려면
 * <b>스프링 컨텍스트가 두 벌</b> 필요하다. 컨텍스트가 늘면 Flyway가 다시 돌아 전체 테스트가 느려진다.
 *
 * <p>그리고 확인하려는 명제 자체가 애플리케이션 로직이 아니라 <b>풀과 락의 상호작용</b>이다.
 * 같은 컨테이너에 작은 풀을 직접 만들어 재현하면, 두 조건을 한 테스트 안에서 정확히 통제할 수 있다.
 *
 * <p>대신 이것이 보이는 것은 <b>기전(mechanism)</b>이지 실제 API 응답 시간이 아니다.
 * 보고서에도 그렇게 적는다. 잰 것과 추론한 것을 섞지 않는다.
 *
 * <h2>실험 구성</h2>
 *
 * <pre>
 * 풀 크기 5, 커넥션 대기 1초
 *
 *   점유 스레드 1개  : 한 행을 FOR UPDATE로 잡고 3초간 들고 있는다  → 커넥션 1개 사용
 *   경합 스레드 4개  : 같은 행을 FOR UPDATE로 요청한다 → 대기         → 커넥션 4개 사용
 *   ─────────────────────────────────────────────────────────
 *   합계 5개. 풀이 가득 찬다.
 *
 *   무관한 질의     : SELECT 1  ← 락과 아무 상관이 없다. 이게 되는가?
 * </pre>
 */
@DisplayName("락 타임아웃과 커넥션 풀 고갈")
class ConnectionPoolExhaustionTest extends PostgresIntegrationTest {

    /** 점유 스레드가 락을 들고 있는 시간. 경합 스레드가 확실히 대기 상태에 들어갈 만큼 길게 잡는다. */
    private static final long HOLD_MS = 6_000;

    /** 풀 크기. 점유 1 + 경합 4 = 5로 정확히 채운다. */
    private static final int POOL_SIZE = 5;

    /** 경합 스레드 수. */
    private static final int CONTENDERS = 4;

    /** 커넥션을 얻지 못했을 때 얼마나 기다릴지. 짧게 잡아야 "못 얻었다"가 빨리 드러난다. */
    private static final long CONNECTION_TIMEOUT_MS = 1_000;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 이 테스트가 잠글 행. 다른 테스트의 데이터를 건드리지 않으려고 전용으로 만든다. */
    private long productId;

    @BeforeEach
    void setUp() {
        String name = "락실험-" + UUID.randomUUID();
        // category는 NOT NULL이다(V12). 화면 분류에 쓰이는 값이라 이 실험과는 무관하지만
        // 넣지 않으면 제약에 걸린다.
        jdbcTemplate.update(
                "INSERT INTO product (product_name, product_price, product_stock, "
                        + "category, subcategory) VALUES (?, ?, ?, ?, ?)",
                name, 1000, 100, "스킨케어", "크림·로션");
        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE product_name = ?", Long.class, name);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
    }

    @Test
    @DisplayName("타임아웃이 없으면 락과 무관한 질의도 커넥션을 못 얻는다")
    void withoutTimeout_unrelatedQueryStarves() throws Exception {
        Outcome outcome = run(0);   // 0 = PostgreSQL에서 '무제한'

        // 경합 스레드 넷이 락을 기다리며 커넥션을 놓지 않는다.
        // 풀에 남은 자리가 없으니, 락과 아무 상관 없는 SELECT 1조차 커넥션을 얻지 못한다.
        assertThat(outcome.probeOk)
                .as("타임아웃이 없으면 무관한 질의가 한 번도 성공하지 못해야 한다. 결과: %s", outcome)
                .isZero();
        assertThat(outcome.probeFailed)
                .as("탐침이 아예 실행되지 않았다. 창 길이를 확인해야 한다")
                .isPositive();

        // 그리고 아무도 스스로 포기하지 않는다. 점유 스레드가 놓아줄 때까지 전부 매달려 있다.
        assertThat(outcome.contendersTimedOut)
                .as("타임아웃이 없으므로 락 대기로 실패한 스레드는 없어야 한다")
                .isZero();

        // 실패 원인이 "커넥션을 못 얻었다"인지 확인한다. 다른 이유로 실패했다면
        // 이 테스트는 풀 고갈이 아니라 엉뚱한 것을 증명하고 있는 것이다.
        assertThat(outcome.failure)
                .as("풀 고갈이 아닌 다른 이유로 실패했다: %s", outcome.failure)
                .contains("Connection is not available");

        System.out.printf("%n[측정] 락 타임아웃 없음 — 무관한 질의 %d회 시도, 성공 %d회 (성공률 0%%). "
                + "매번 %dms를 기다리다 커넥션을 얻지 못했다. "
                + "경합 스레드 %d개가 락을 기다리며 커넥션을 계속 붙잡고 있다%n",
                outcome.probeFailed + outcome.probeOk, outcome.probeOk,
                outcome.unrelatedMillis, CONTENDERS);
    }

    @Test
    @DisplayName("타임아웃이 있으면 대기 스레드가 커넥션을 돌려줘 무관한 질의가 산다")
    void withTimeout_unrelatedQuerySurvives() throws Exception {
        Outcome outcome = run(500);

        // 경합 스레드가 500ms 뒤 포기하고 커넥션을 반납한다. 풀에 자리가 생긴다.
        assertThat(outcome.probeFailed)
                .as("타임아웃이 있으면 무관한 질의가 실패하면 안 된다. 결과: %s", outcome)
                .isZero();
        assertThat(outcome.probeOk)
                .as("탐침이 아예 실행되지 않았다")
                .isPositive();

        assertThat(outcome.contendersTimedOut)
                .as("경합 스레드는 락 대기 타임아웃으로 실패해야 한다")
                .isEqualTo(CONTENDERS);

        // 탐침이 즉시 성공하지는 않는다. 커넥션이 빌 때까지 기다렸다가 얻는다.
        // 중요한 것은 "느려지되 죽지 않는다"이고, 타임아웃이 없으면 아예 못 얻는다.
        System.out.printf("%n[측정] 락 타임아웃 500ms — 무관한 질의 %d회 시도, 성공 %d회 (성공률 100%%). "
                + "가장 오래 걸린 것이 %dms. "
                + "경합 스레드 %d개가 스스로 포기하고 커넥션을 반납했다%n",
                outcome.probeOk + outcome.probeFailed, outcome.probeOk,
                outcome.unrelatedMillis, outcome.contendersTimedOut);
    }

    // ────────────────────────────────────────────────────────────

    /**
     * 실험 한 회.
     *
     * @param lockTimeoutMs 락 대기 상한. 0이면 무제한
     */
    private Outcome run(int lockTimeoutMs) throws Exception {
        try (HikariDataSource pool = smallPool(lockTimeoutMs)) {
            ExecutorService threads = Executors.newFixedThreadPool(CONTENDERS + 1);

            // 점유 스레드가 락을 확실히 잡은 뒤에 경합을 시작해야 한다.
            // 순서가 뒤집히면 경합 스레드가 먼저 락을 가져가 실험이 성립하지 않는다.
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch contendersStarted = new CountDownLatch(CONTENDERS);
            AtomicInteger timedOut = new AtomicInteger();

            threads.submit(() -> holdLock(pool, locked));
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("점유 스레드가 락을 잡지 못했다").isTrue();
            long lockedAt = System.nanoTime();

            for (int i = 0; i < CONTENDERS; i++) {
                threads.submit(() -> contend(pool, contendersStarted, timedOut));
            }

            // 경합 스레드가 전부 커넥션을 쥐고 대기 상태에 들어갈 시간을 준다.
            // 이 시점부터 풀에는 빈 자리가 없어야 한다.
            assertThat(contendersStarted.await(10, TimeUnit.SECONDS))
                    .as("경합 스레드가 시작되지 않았다").isTrue();
            Thread.sleep(300);

            // 관측 창은 점유 스레드가 락을 놓기 전까지다. 그 뒤에는 경합이 사라져
            // 무엇을 재든 성공하므로, 그 시각이 창에 섞이면 결과가 오염된다.
            //
            // 실제로 창을 넉넉히 잡았다가 마지막 탐침 하나가 점유 해제 시각을 넘겨
            // "성공 1회"가 섞여 나왔다. 락을 잡은 시각을 기준으로 다시 계산한다.
            long deadline = lockedAt
                    + (HOLD_MS - CONNECTION_TIMEOUT_MS - 500) * 1_000_000;
            Outcome outcome = probeUnrelated(pool, deadline);

            // 타임아웃 집계는 스레드가 전부 끝난 뒤에 읽는다.
            // 탐침 직후에 읽으면 아직 대기 중인 스레드가 세어지지 않아,
            // 500ms 설정에서 "1개만 포기했다" 같은 값이 나온다. 실제로 그렇게 한 번 틀렸다.
            threads.shutdown();
            threads.awaitTermination(20, TimeUnit.SECONDS);
            outcome.contendersTimedOut = timedOut.get();
            return outcome;
        }
    }

    /**
     * 락과 아무 상관 없는 질의를 경합이 이어지는 동안 반복해서 던진다.
     * 상품 조회나 로그인에 해당하는 자리다.
     *
     * <p>한 번만 던지면 결과가 타이밍에 좌우된다. 경합 스레드들이 조금씩 다른 시각에
     * 대기를 시작하므로, 운 좋게 자리가 비어 있는 순간에 걸리면 "괜찮다"는 결론이 나온다.
     * 실제로 처음에는 그렇게 만들었다가 <b>70ms 만에 성공</b>하는 것을 보고 고쳤다.
     *
     * <p>대신 창(window) 동안 반복해서 던지고 <b>성공률</b>을 센다.
     * 알고 싶은 것은 "한 번 됐는가"가 아니라 "그동안 서비스가 살아 있었는가"다.
     */
    private Outcome probeUnrelated(HikariDataSource pool, long deadlineNanos) {
        Outcome outcome = new Outcome();
        long slowest = 0;

        // 새 탐침을 '시작'하는 시점만 창 안으로 제한한다. 마감 직전에 시작한 탐침 하나는
        // 커넥션 대기 시간만큼 더 걸릴 수 있으므로, 창 자체를 그만큼 앞당겨 잡는다.
        while (System.nanoTime() < deadlineNanos) {
            long start = System.nanoTime();
            try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT 1").close();
                outcome.probeOk++;
            } catch (SQLException e) {
                outcome.probeFailed++;
                outcome.failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            slowest = Math.max(slowest, (System.nanoTime() - start) / 1_000_000);
        }

        outcome.unrelatedMillis = slowest;
        return outcome;
    }

    /** 행 하나를 잡고 일정 시간 들고 있는다. 인기 상품에 긴 트랜잭션이 걸린 상황이다. */
    private void holdLock(HikariDataSource pool, CountDownLatch locked) {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeQuery("SELECT id FROM product WHERE id = " + productId + " FOR UPDATE")
                        .close();
                locked.countDown();
                Thread.sleep(HOLD_MS);
            }
            c.rollback();
        } catch (Exception e) {
            locked.countDown();   // 실패해도 본 실험이 영원히 기다리지 않게 한다
        }
    }

    /** 같은 행을 요청해 대기 상태로 들어간다. 대기하는 동안 커넥션을 놓지 않는다. */
    private void contend(HikariDataSource pool, CountDownLatch started, AtomicInteger timedOut) {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            started.countDown();
            try (Statement s = c.createStatement()) {
                s.executeQuery("SELECT id FROM product WHERE id = " + productId + " FOR UPDATE")
                        .close();
            }
            c.rollback();
        } catch (SQLException e) {
            // lock_timeout에 걸려 끊긴 경우. 이게 우리가 원하는 동작이다.
            timedOut.incrementAndGet();
            started.countDown();
        }
    }

    /**
     * 같은 컨테이너를 가리키는 작은 풀.
     *
     * <p>애플리케이션의 풀을 쓰지 않는다. 크기를 줄이고 타임아웃을 바꿔야 하는데,
     * 운영 풀을 건드리면 같은 컨텍스트를 쓰는 다른 테스트에 영향이 간다.
     */
    private HikariDataSource smallPool(int lockTimeoutMs) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        config.setUsername(environment.getRequiredProperty("spring.datasource.username"));
        config.setPassword(environment.getRequiredProperty("spring.datasource.password"));
        config.setMaximumPoolSize(POOL_SIZE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);

        // 세션 단위 락 대기 상한. 운영에서 쓰는 것과 같은 방식이다. (D22)
        // PostgreSQL의 FOR UPDATE에는 대기 시간 문법이 없어 여기에 건다.
        config.addDataSourceProperty("options", "-c lock_timeout=" + lockTimeoutMs);
        config.setPoolName("exhaustion-test-" + lockTimeoutMs);
        return new HikariDataSource(config);
    }

    /** 실험 한 회의 결과. */
    private static class Outcome {
        /** 무관한 질의가 성공한 횟수. */
        int probeOk;
        /** 무관한 질의가 커넥션을 얻지 못한 횟수. */
        int probeFailed;
        /** 가장 오래 걸린 탐침. 실패한 경우는 커넥션을 포기하기까지 걸린 시간이다. */
        long unrelatedMillis;
        int contendersTimedOut;
        String failure;

        @Override
        public String toString() {
            return "무관한 질의 성공 %d / 실패 %d (최대 %dms), 경합 타임아웃 %d건%s".formatted(
                    probeOk, probeFailed, unrelatedMillis, contendersTimedOut,
                    failure == null ? "" : " — " + failure);
        }
    }

    /** 검사 대상이 실제로 존재하는지. 잘못된 전제 위에서 초록불이 뜨는 것을 막는다. */
    @Test
    @DisplayName("실험 전제 — 풀 크기가 점유 1 + 경합 수와 정확히 같다")
    void poolSizeMatchesThreadCount() {
        // 풀이 크면 무관한 질의가 그냥 성공해서 첫 번째 테스트가 아무것도 검증하지 못한다.
        assertThat(POOL_SIZE)
                .as("풀에 여유가 있으면 고갈이 재현되지 않는다")
                .isEqualTo(CONTENDERS + 1);

        assertThat(List.of(HOLD_MS, CONNECTION_TIMEOUT_MS))
                .as("점유 시간이 커넥션 대기 시간보다 길어야 '못 얻었다'가 관측된다")
                .satisfies(v -> assertThat(HOLD_MS).isGreaterThan(CONNECTION_TIMEOUT_MS));
    }
}
