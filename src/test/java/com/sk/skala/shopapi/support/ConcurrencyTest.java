package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.concurrency.ConcurrencyProperties;
import com.sk.skala.shopapi.global.concurrency.RetryMetrics;
import com.sk.skala.shopapi.global.concurrency.TransactionRetryExecutor;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.global.idempotency.IdempotentExecutor;
import com.sk.skala.shopapi.order.app.OrderService;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * 동시성 제어 검증. P4의 핵심이다. (D22)
 *
 * <p>확인하려는 것은 하나다. <b>수백 개 요청이 같은 순간에 들어와도 숫자가 맞는가.</b>
 *
 * <h2>왜 실제 PostgreSQL이어야 하는가</h2>
 *
 * <p>H2는 행 수준 락의 의미가 다르다. H2에서 통과한 비관적 락 테스트는
 * 운영 환경을 전혀 보장하지 못한다. P3에서 만든 컨테이너 harness가 여기의 전제다. (D21)
 *
 * <h2>왜 {@code @Transactional}이 없는가</h2>
 *
 * <p>다른 통합 테스트와 달리 이 클래스에는 {@code @Transactional}이 없다.
 * 붙이면 모든 스레드가 <b>하나의 테스트 트랜잭션을 공유</b>해 애초에 경합이 생기지 않는다.
 * 커밋되지 않으므로 낙관적 락 버전도 올라가지 않는다. 검증하려는 것이 사라지는 셈이다.
 *
 * <p>대신 롤백이 없으므로 뒷정리를 손으로 한다. 컨테이너를 다른 테스트와 공유하기 때문에
 * <b>이 테스트가 만든 행만</b> 지운다. 이름에 UUID를 섞어 구분한다.
 *
 * <h2>어디를 호출하는가</h2>
 *
 * <p>MockMvc가 아니라 {@code IdempotentExecutor}를 직접 부른다. 검증 대상이 HTTP 계층이
 * 아니라 그 아래 <b>멱등성 → 재시도 → 트랜잭션</b> 세 겹이기 때문이다.
 * 스레드마다 SecurityContext를 심는 수고도 줄어든다.
 */
@DisplayName("동시성 제어")
class ConcurrencyTest extends PostgresIntegrationTest {

    /** 스레드가 모두 준비될 때까지 기다렸다가 한꺼번에 출발시키기 위한 대기 한도. */
    private static final int TIMEOUT_SECONDS = 60;

    @Autowired
    private OrderService orderService;

    @Autowired
    private IdempotentExecutor idempotentExecutor;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RetryMetrics metrics;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 이 테스트가 만든 행을 구분하는 꼬리표. 공유 컨테이너를 오염시키지 않기 위해서다. */
    private String tag;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        metrics.reset();
    }

    @AfterEach
    void tearDown() {
        // 순서를 지켜야 한다. 참조하는 쪽(order_item, idempotency_key)을 먼저 지우지 않으면
        // 외래 키 제약에 걸린다.
        jdbc.update("DELETE FROM order_item WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM idempotency_key WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM customer WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM product WHERE product_name LIKE ?", tag + "%");
    }

    private Customer 고객(String suffix, long point) {
        return customerRepository.save(
                new Customer(tag + suffix, "$2a$10$h", Money.of(point)));
    }

    private Product 상품(String suffix, long price, int stock) {
        return productRepository.save(new Product(tag + suffix, Money.of(price), stock));
    }

    private long 잔액(String customerId) {
        return jdbc.queryForObject(
                "SELECT customer_point FROM customer WHERE customer_id = ?", Long.class, customerId);
    }

    private int 재고(Long productId) {
        return jdbc.queryForObject(
                "SELECT product_stock FROM product WHERE id = ?", Integer.class, productId);
    }

    /** 주문 한 건을 전체 계층(멱등성 → 재시도 → 트랜잭션)을 거쳐 실행한다. */
    private void 주문(String customerId, Long productId, int quantity) {
        OrderRequest request = new OrderRequest(productId, quantity);
        idempotentExecutor.execute(
                UUID.randomUUID().toString(), customerId, request, OrderListResponse.class,
                () -> orderService.placeOrder(customerId, request));
    }

    /**
     * 여러 스레드를 <b>같은 순간에</b> 출발시킨다.
     *
     * <p>그냥 submit만 하면 앞선 스레드가 이미 끝난 뒤에 뒤 스레드가 시작해 경합이
     * 거의 생기지 않는다. 시작 신호를 걸어 모두 준비된 뒤 한꺼번에 풀어야
     * 실제로 같은 행을 다투는 상황이 만들어진다.
     *
     * @return 각 작업이 던진 예외 목록. 성공한 작업은 아무것도 남기지 않는다
     */
    private List<Throwable> 동시실행(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 50));
        CountDownLatch 시작신호 = new CountDownLatch(1);
        CountDownLatch 완료 = new CountDownLatch(threads);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        시작신호.await();
                        task.run();
                    } catch (Throwable e) {
                        failures.add(e);
                    } finally {
                        완료.countDown();
                    }
                });
            }

            시작신호.countDown();
            assertThat(완료.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("%d개 스레드가 %d초 안에 끝나야 한다", threads, TIMEOUT_SECONDS)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        return failures;
    }

    private long 개수(List<Throwable> failures, ErrorCode code) {
        return failures.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .filter(e -> e.getErrorCode() == code)
                .count();
    }

    /**
     * 재고는 <b>여러 사용자가 하나의 행을 다투는</b> 자원이다. 비관적 락으로 보호한다.
     *
     * <p>락이 없으면 두 트랜잭션이 같은 재고를 읽고 둘 다 검사를 통과해
     * 재고보다 많이 팔린다. 이것이 오버셀링이다.
     */
    @Nested
    @DisplayName("비관적 락 — 재고")
    class PessimisticStock {

        @Test
        @DisplayName("재고 100개에 200명이 동시에 주문하면 정확히 100명만 성공한다")
        void exactlyStockManySucceed() throws InterruptedException {
            Product product = 상품("-인기상품", 1_000, 100);

            // 고객을 200명 따로 만든다. 한 명이 200번 주문하면 Customer 행에서도
            // 경합이 생겨 재고 락만 보려는 이 테스트의 목적이 흐려진다.
            List<String> customers = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                customers.add(고객("-c%03d".formatted(i), 1_000_000).getCustomerId());
            }

            AtomicInteger index = new AtomicInteger();
            List<Throwable> failures = 동시실행(200,
                    () -> 주문(customers.get(index.getAndIncrement()), product.getId(), 1));

            // 정확히 100건이 실패해야 한다. 99건이면 하나가 덜 팔린 것이고,
            // 101건이면 하나가 더 팔린 것이다.
            assertThat(failures).hasSize(100);
            assertThat(개수(failures, ErrorCode.OUT_OF_STOCK)).isEqualTo(100);

            // 오버셀링이 있었다면 CHECK 제약에 걸려 다른 예외가 났거나 재고가 음수가 된다.
            assertThat(재고(product.getId())).isZero();

            // 주문 항목도 정확히 100건이어야 한다. 재고만 맞고 항목이 더 많으면
            // 어딘가에서 재고 없이 주문이 들어간 것이다.
            Integer orders = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_item WHERE product_id = ?",
                    Integer.class, product.getId());
            assertThat(orders).isEqualTo(100);

            System.out.printf("""

                    ── 비관적 락: 재고 100 / 요청 200 ──
                    성공            %d
                    OUT_OF_STOCK    %d
                    남은 재고        %d
                    낙관적 충돌      %d   ← 재고 락이 앞에서 직렬화하므로 0에 가깝다
                    락 타임아웃      %d
                    %n""",
                    200 - failures.size(), 개수(failures, ErrorCode.OUT_OF_STOCK),
                    재고(product.getId()), metrics.conflicts(), metrics.lockTimeouts());
        }

        @Test
        @DisplayName("재고보다 많은 수량을 한 번에 주문하면 거부한다")
        void rejectOverStock() {
            Product product = 상품("-소량", 1_000, 5);
            String customer = 고객("-buyer", 1_000_000).getCustomerId();

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> 주문(customer, product.getId(), 6))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OUT_OF_STOCK);

            assertThat(재고(product.getId())).isEqualTo(5);
        }
    }

    /**
     * 포인트는 <b>본인만 바꾸는</b> 자원이다. 경합이 드물어 낙관적 락을 쓴다.
     *
     * <p>드물다는 것이 없다는 뜻은 아니다. 같은 사람이 여러 창에서 주문하거나
     * 클라이언트가 재시도하면 충돌한다. 그때 갱신 유실이 나면 안 된다.
     */
    @Nested
    @DisplayName("낙관적 락 — 포인트")
    class OptimisticPoint {

        /** 상품 여러 개를 만든다. 같은 상품이면 재고 락이 앞에서 직렬화해 충돌이 안 생긴다. */
        private List<Long> 상품들(String prefix, int count) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add(상품("-%s%02d".formatted(prefix, i), 1_000, 100).getId());
            }
            return ids;
        }

        @Test
        @DisplayName("현실적인 경합(동시 5건)은 재시도가 모두 흡수한다")
        void realisticContentionIsAbsorbed() throws InterruptedException {
            String customer = 고객("-normal", 1_000_000).getCustomerId();
            List<Long> products = 상품들("n", 5);

            AtomicInteger index = new AtomicInteger();
            List<Throwable> failures = 동시실행(5,
                    () -> 주문(customer, products.get(index.getAndIncrement()), 1));

            // 낙관적 락을 고른 전제가 "경합이 드물다"였다. 그 전제 안에서는
            // 사용자가 충돌을 느끼지 않아야 한다.
            assertThat(failures).isEmpty();
            assertThat(잔액(customer)).isEqualTo(995_000L);
        }

        /**
         * 경합이 전제를 넘어서면 일부 요청이 거절된다. <b>그래도 숫자는 틀리지 않는다.</b>
         *
         * <p>이 구분이 중요하다. 거절은 정직한 배압(backpressure)이고, 잔액이 어긋나는 것은
         * 데이터 손상이다. 앞의 것은 클라이언트가 다시 시도하면 되지만 뒤의 것은 되돌릴 수 없다.
         */
        @Test
        @DisplayName("과부하에서 일부가 거절되어도 잔액은 성공 건수와 정확히 일치한다")
        void noLostUpdateEvenUnderOverload() throws InterruptedException {
            String customer = 고객("-heavy", 1_000_000).getCustomerId();
            List<Long> products = 상품들("h", 20);

            AtomicInteger index = new AtomicInteger();
            List<Throwable> failures = 동시실행(20,
                    () -> 주문(customer, products.get(index.getAndIncrement()), 1));

            // 실패했다면 전부 CONCURRENT_MODIFICATION이어야 한다.
            // 다른 예외가 섞였다면 재시도 계층이 엉뚱한 것을 삼키고 있다는 뜻이다.
            assertThat(개수(failures, ErrorCode.CONCURRENT_MODIFICATION))
                    .isEqualTo(failures.size());

            long 성공 = 20 - failures.size();

            // 핵심 단언이다. 갱신 유실이 있었다면 차감이 사라져 잔액이 이보다 크고,
            // 실패한 요청이 부분 반영됐다면 이보다 작다. @Version이 없으면 여기서 깨진다.
            assertThat(잔액(customer))
                    .as("성공 %d건 × 1,000원만 차감되어야 한다", 성공)
                    .isEqualTo(1_000_000L - 성공 * 1_000L);

            // 재고도 성공한 건수만큼만 줄어야 한다.
            long 팔린수량 = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE customer_id = ?",
                    Long.class, customer);
            assertThat(팔린수량).isEqualTo(성공);

            System.out.printf("""

                    ── 낙관적 락: 한 고객 / 동시 20건 / 재시도 3회 ──
                    성공            %d
                    거절            %d   (CONCURRENT_MODIFICATION)
                    낙관적 충돌      %d
                    재시도 횟수 합   %d
                    최종 잔액        %,d
                    %n""",
                    성공, failures.size(), metrics.conflicts(),
                    metrics.retriedAttempts(), 잔액(customer));
        }

        /**
         * 재시도 횟수를 바꿔가며 <b>같은 경합에서 성공률이 어떻게 변하는지</b> 잰다.
         *
         * <p>로드맵이 P4의 완료 조건으로 요구하는 측정치가 이것이다.
         * "재시도 3회면 충분하다" 같은 문장을 근거 없이 쓰지 않기 위해서다.
         *
         * <p>{@link TransactionRetryExecutor}를 직접 만들어 쓴다. 설정값을 바꾸려면
         * 스프링 컨텍스트를 새로 띄워야 하는데, 생성자가 평범해서 그럴 필요가 없다.
         * 멱등성 계층도 끼우지 않는다. 재시도 횟수 하나만 변수로 두기 위해서다.
         */
        @Test
        @DisplayName("[측정] 재시도 횟수에 따른 성공률 — 동시 20건")
        void measureRetryAttempts() throws InterruptedException {
            StringBuilder table = new StringBuilder("""

                    ── [측정] 한 고객에 동시 20건, 재시도 횟수별 ──
                    시도  성공  거절  충돌  재시도합  소요(ms)
                    """);

            for (int attempts : new int[] {1, 3, 5, 10, 20}) {
                metrics.reset();

                String customer = 고객("-m" + attempts, 1_000_000).getCustomerId();
                List<Long> products = 상품들("m" + attempts, 20);

                TransactionRetryExecutor executor = new TransactionRetryExecutor(
                        new ConcurrencyProperties(attempts, Duration.ofMillis(50),
                                Duration.ofSeconds(1)),
                        metrics);

                AtomicInteger index = new AtomicInteger();
                long start = System.nanoTime();
                List<Throwable> failures = 동시실행(20, () -> {
                    Long productId = products.get(index.getAndIncrement());
                    executor.execute(() -> orderService.placeOrder(
                            customer, new OrderRequest(productId, 1)));
                });
                long elapsed = (System.nanoTime() - start) / 1_000_000;

                long 성공 = 20 - failures.size();

                // 어느 설정에서든 숫자는 틀리면 안 된다. 재시도는 성공률을 바꿀 뿐
                // 정확성을 바꾸지 않는다.
                assertThat(잔액(customer)).isEqualTo(1_000_000L - 성공 * 1_000L);

                table.append("%3d  %4d  %4d  %4d  %7d  %7d%n".formatted(
                        attempts, 성공, failures.size(),
                        metrics.conflicts(), metrics.retriedAttempts(), elapsed));
            }

            System.out.println(table);
        }
    }

    /**
     * 락 대기가 무한하면 국소적인 경합이 전역 장애로 번진다.
     *
     * <p>인기 상품 한 행에 요청이 몰리면 대기가 길어지고, 대기하는 동안 커넥션을
     * 계속 붙잡아 풀이 고갈된다. 그러면 <b>락과 무관한 API까지 함께 멈춘다.</b>
     * {@code lock_timeout}이 그 연쇄를 끊는다.
     */
    @Nested
    @DisplayName("락 타임아웃")
    class LockTimeout {

        @Test
        @DisplayName("앞선 트랜잭션이 행을 오래 잡고 있으면 LOCK_TIMEOUT으로 끊는다")
        void lockTimeoutBreaksTheChain() throws InterruptedException {
            Product product = 상품("-잠긴상품", 1_000, 100);
            String customer = 고객("-waiter", 1_000_000).getCustomerId();

            CountDownLatch 락획득 = new CountDownLatch(1);
            CountDownLatch 놓아줌 = new CountDownLatch(1);

            Thread holder = new Thread(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        // 테스트 프로파일의 lock_timeout은 1초다.
                        // 이 트랜잭션이 그보다 오래 잡고 있으면 뒤 요청이 끊긴다.
                        productRepository.findByIdForUpdate(product.getId());
                        락획득.countDown();
                        try {
                            놓아줌.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
            holder.start();

            try {
                assertThat(락획득.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

                long start = System.nanoTime();
                List<Throwable> failures = 동시실행(1, () -> 주문(customer, product.getId(), 1));
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                assertThat(개수(failures, ErrorCode.LOCK_TIMEOUT))
                        .as("무한정 기다리지 않고 LOCK_TIMEOUT으로 끊어야 한다")
                        .isEqualTo(1);

                // 1초 언저리에서 끊겨야 한다. 훨씬 길면 설정이 먹지 않은 것이다.
                assertThat(elapsedMs)
                        .as("lock_timeout 1초 안팎에서 끊긴다 (실측 %dms)", elapsedMs)
                        .isBetween(500L, 5_000L);

                assertThat(metrics.lockTimeouts()).isEqualTo(1);

                System.out.printf("%n── 락 타임아웃 실측: %dms (설정 1000ms) ──%n%n", elapsedMs);

            } finally {
                놓아줌.countDown();
                holder.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
        }
    }
}
