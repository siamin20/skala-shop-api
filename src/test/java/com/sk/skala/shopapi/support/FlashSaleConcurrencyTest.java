package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.event.app.FlashSaleService;
import com.sk.skala.shopapi.event.app.FlashSaleStrategies;
import com.sk.skala.shopapi.event.app.FlashSaleStrategy;
import com.sk.skala.shopapi.event.domain.FlashSale;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.event.dto.FlashSaleOrderRequest;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.concurrency.RetryMetrics;
import com.sk.skala.shopapi.global.concurrency.TransactionRetryExecutor;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.global.idempotency.IdempotentExecutor;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * 선착순 이벤트 — 네 가지 수량 보호 방식 비교. P4-B의 핵심이다. (D23)
 *
 * <p>한정 수량 하나에 수백 건이 몰리는, 이 프로젝트에서 가장 극단적인 경합이다.
 * P4-A의 상품 재고는 상품마다 행이 나뉘어 경합이 흩어지지만 여기서는 모두가
 * <b>정확히 같은 하나의 행</b>을 노린다.
 *
 * <h2>두 종류의 시험을 나눠서 한다</h2>
 *
 * <table border="1">
 *   <tr><th></th><th>무엇을 보는가</th><th>왜 나누는가</th></tr>
 *   <tr>
 *     <td>정확성</td><td>전체 참여 흐름에서 정확히 N개만 팔리는가</td>
 *     <td>실제 사용 경로에서 숫자가 맞는지가 먼저다</td>
 *   </tr>
 *   <tr>
 *     <td>처리량</td><td>수량 차감 <b>그 부분만</b> 떼어낸 성능</td>
 *     <td>전체 흐름에는 상품 재고의 비관적 락이 끼어 있어,
 *         그대로 재면 <b>네 전략의 차이가 그 락에 묻힌다</b></td>
 *   </tr>
 * </table>
 *
 * <p>두 번째 이유가 P4-A에서 얻은 교훈이다. 앞에 있는 락이 뒤에 있는 락의 일감을
 * 가져가므로, 비교하려는 부분만 떼어내지 않으면 아무 차이도 관측되지 않는다.
 */
@DisplayName("선착순 이벤트 — 전략 비교")
class FlashSaleConcurrencyTest extends PostgresIntegrationTest {

    private static final int TIMEOUT_SECONDS = 120;

    /** 한정 수량. */
    private static final int LIMIT = 50;

    /** 동시 요청 수. 수량의 몇 배가 몰리는 상황을 만든다. */
    private static final int REQUESTS = 500;

    @Autowired
    private FlashSaleService flashSaleService;

    @Autowired
    private FlashSaleStrategies strategies;

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private IdempotentExecutor idempotentExecutor;

    @Autowired
    private TransactionRetryExecutor retryExecutor;

    @Autowired
    private RetryMetrics metrics;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String tag;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        tx = new TransactionTemplate(transactionManager);
        metrics.reset();
    }

    @AfterEach
    void tearDown() {
        // 컨테이너를 다른 테스트와 공유하므로 이 테스트가 만든 행만 지운다.
        // 참조하는 쪽부터 지워야 외래 키에 걸리지 않는다.
        jdbc.update("DELETE FROM order_item WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM idempotency_key WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM customer WHERE customer_id LIKE ?", tag + "%");
        jdbc.update("DELETE FROM flash_sale WHERE sale_name LIKE ?", tag + "%");
        jdbc.update("DELETE FROM product WHERE product_name LIKE ?", tag + "%");
    }

    /**
     * 이벤트 하나를 만든다.
     *
     * <p>상품 재고를 넉넉히 준다. 재고가 모자라면 이벤트 수량이 아니라 재고가 한도가 되어
     * 무엇을 재는 것인지 알 수 없게 된다.
     */
    private FlashSale 이벤트(String suffix, int limit) {
        Product product = productRepository.save(
                new Product(tag + suffix + "-상품", Money.of(1_000), REQUESTS + limit));

        Instant now = Instant.now();
        return flashSaleRepository.save(new FlashSale(
                tag + suffix, product, limit,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)));
    }

    private List<String> 고객들(String prefix, int count) {
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            customers.add(new Customer(
                    "%s-%s%04d".formatted(tag, prefix, i), "$2a$10$h", Money.of(1_000_000)));
        }
        return customerRepository.saveAll(customers).stream()
                .map(Customer::getCustomerId).toList();
    }

    private List<Throwable> 동시실행(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch 시작신호 = new CountDownLatch(1);
        CountDownLatch 완료 = new CountDownLatch(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

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
                    .as("%d개 요청이 %d초 안에 끝나야 한다", threads, TIMEOUT_SECONDS)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    private int 남은수량(Long saleId) {
        return jdbc.queryForObject(
                "SELECT remaining FROM flash_sale WHERE id = ?", Integer.class, saleId);
    }

    // ────────────────────────── 정확성 ──────────────────────────

    /**
     * 네 전략 모두 전체 참여 흐름에서 초과 판매가 없어야 한다.
     *
     * <p>낙관적 락만 예외적으로 <b>덜 팔릴 수 있다.</b> 재시도를 소진한 요청이 거절되기
     * 때문이다. 덜 파는 것은 손해지만 데이터가 깨지는 것은 아니다.
     * 초과 판매(더 팔림)와는 성격이 전혀 다르므로 단언을 나눠서 한다.
     */
    @Test
    @DisplayName("네 전략 모두 한정 수량을 넘겨 팔지 않는다")
    void neverOversells() throws InterruptedException {
        StringBuilder table = new StringBuilder("""

                ── [정확성] 한정 %d개 / 동시 %d건 / 전체 참여 흐름 ──
                전략             성공  품절거절  기타거절  남은수량  소요(ms)
                """.formatted(LIMIT, REQUESTS));

        for (FlashSaleStrategy strategy : FlashSaleStrategy.values()) {
            metrics.reset();

            FlashSale sale = 이벤트("-정확성-" + strategy, LIMIT);
            flashSaleService.prepare(sale.getId(), strategy);
            List<String> customers = 고객들("a" + strategy.ordinal(), REQUESTS);

            AtomicInteger index = new AtomicInteger();
            long start = System.nanoTime();
            List<Throwable> failures = 동시실행(REQUESTS, () -> {
                String customerId = customers.get(index.getAndIncrement());
                FlashSaleOrderRequest request = new FlashSaleOrderRequest(sale.getId(), 1);
                // 운영과 같은 경로다. 멱등성 → 재시도 → 트랜잭션 세 겹을 모두 거친다.
                idempotentExecutor.execute(
                        UUID.randomUUID().toString(), customerId, request,
                        OrderListResponse.class,
                        () -> flashSaleService.purchase(customerId, request, strategy));
            });
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            long 성공 = REQUESTS - failures.size();
            long 품절 = 개수(failures, ErrorCode.SOLD_OUT);
            long 기타 = failures.size() - 품절;

            // 초과 판매는 어떤 전략에서도 허용되지 않는다.
            assertThat(성공)
                    .as("%s — 한정 수량을 넘겨 팔면 안 된다", strategy)
                    .isLessThanOrEqualTo(LIMIT);

            // 팔린 수량과 남은 수량의 합은 항상 처음 수량이다.
            assertThat(남은수량(sale.getId()))
                    .as("%s — 남은 수량은 판매량과 정확히 맞아야 한다", strategy)
                    .isEqualTo((int) (LIMIT - 성공));

            // 주문 항목 수도 성공 건수와 같아야 한다.
            // 수량만 맞고 주문이 더 많으면 어딘가에서 수량 없이 주문이 들어간 것이다.
            Integer orders = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_item WHERE product_id = ?",
                    Integer.class, sale.getProduct().getId());
            assertThat(orders).as("%s — 주문 수", strategy).isEqualTo((int) 성공);

            table.append("%-14s %5d %8d %9d %9d %9d%n".formatted(
                    strategy, 성공, 품절, 기타, 남은수량(sale.getId()), elapsed));

            tearDown();
            setUp();
        }

        System.out.println(table);
    }

    /**
     * 나머지 셋은 <b>정확히</b> 한도만큼 팔아야 한다.
     *
     * <p>낙관적 락을 이 단언에서 뺀 이유는, 경합이 심하면 재시도를 소진해 덜 팔리는 것이
     * 그 전략의 정상 동작이기 때문이다. 함께 묶으면 "낙관적 락이 틀렸다"가 아니라
     * "전제가 맞지 않는 곳에 썼다"는 사실이 가려진다.
     */
    @Test
    @DisplayName("낙관적 락을 뺀 세 전략은 정확히 한도만큼 판다")
    void sellsExactlyTheLimit() throws InterruptedException {
        List<FlashSaleStrategy> exact = List.of(
                FlashSaleStrategy.PESSIMISTIC,
                FlashSaleStrategy.ATOMIC_UPDATE,
                FlashSaleStrategy.REDIS);

        for (FlashSaleStrategy strategy : exact) {
            FlashSale sale = 이벤트("-정확-" + strategy, LIMIT);
            flashSaleService.prepare(sale.getId(), strategy);
            List<String> customers = 고객들("b" + strategy.ordinal(), REQUESTS);

            AtomicInteger index = new AtomicInteger();
            List<Throwable> failures = 동시실행(REQUESTS, () -> {
                String customerId = customers.get(index.getAndIncrement());
                FlashSaleOrderRequest request = new FlashSaleOrderRequest(sale.getId(), 1);
                idempotentExecutor.execute(
                        UUID.randomUUID().toString(), customerId, request,
                        OrderListResponse.class,
                        () -> flashSaleService.purchase(customerId, request, strategy));
            });

            assertThat(REQUESTS - failures.size())
                    .as("%s — 정확히 %d건이 성공해야 한다", strategy, LIMIT)
                    .isEqualTo(LIMIT);
            assertThat(남은수량(sale.getId())).isZero();

            tearDown();
            setUp();
        }
    }

    // ────────────────────────── 처리량 ──────────────────────────

    /**
     * 수량 차감 <b>그 부분만</b> 떼어내 잰다.
     *
     * <p>전체 참여 흐름으로 재면 상품 재고의 비관적 락이 모든 요청을 직렬화해
     * 네 전략의 차이가 그 락에 묻힌다. P4-A에서 확인한 "앞의 락이 뒤의 락 일감을
     * 가져간다"가 여기서도 그대로 작동한다.
     *
     * <p>그래서 {@code claim()}만 트랜잭션에 감싸 호출한다.
     * 운영 성능이 아니라 <b>네 방식의 상대 비교</b>가 목적이다.
     */
    @Test
    @DisplayName("[측정] 수량 차감만 떼어낸 전략별 처리량")
    void measureClaimThroughput() throws InterruptedException {
        StringBuilder table = new StringBuilder("""

                ── [처리량] 한정 %d개 / 동시 %d건 / 수량 차감만 ──
                전략             성공  품절  낙관적충돌  재시도합  소요(ms)
                """.formatted(LIMIT, REQUESTS));

        for (FlashSaleStrategy strategy : FlashSaleStrategy.values()) {
            metrics.reset();

            FlashSale sale = 이벤트("-측정-" + strategy, LIMIT);
            flashSaleService.prepare(sale.getId(), strategy);

            long start = System.nanoTime();
            List<Throwable> failures = 동시실행(REQUESTS, () ->
                    // 재시도 계층은 그대로 둔다. 낙관적 락은 재시도 없이는 성립하지 않아,
                    // 빼고 재면 그 전략에만 불리한 비교가 된다.
                    retryExecutor.execute(() -> tx.execute(status -> {
                        strategies.get(strategy).claim(sale.getId(), 1);
                        return null;
                    })));
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            long 성공 = REQUESTS - failures.size();

            assertThat(성공).as("%s — 초과 판매", strategy).isLessThanOrEqualTo(LIMIT);
            assertThat(남은수량(sale.getId())).isEqualTo((int) (LIMIT - 성공));

            table.append("%-14s %5d %5d %11d %9d %9d%n".formatted(
                    strategy, 성공, 개수(failures, ErrorCode.SOLD_OUT),
                    metrics.conflicts(), metrics.retriedAttempts(), elapsed));

            tearDown();
            setUp();
        }

        System.out.println(table);
    }

    @Test
    @DisplayName("종료된 이벤트에는 참여할 수 없다")
    void closedEventRejects() {
        Product product = productRepository.save(
                new Product(tag + "-종료-상품", Money.of(1_000), 100));
        Instant now = Instant.now();
        FlashSale sale = flashSaleRepository.save(new FlashSale(
                tag + "-종료", product, 10,
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)));

        String customer = 고객들("closed", 1).get(0);
        FlashSaleOrderRequest request = new FlashSaleOrderRequest(sale.getId(), 1);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> flashSaleService.purchase(
                        customer, request, FlashSaleStrategy.ATOMIC_UPDATE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOLD_OUT);

        // 기간 검사가 수량 차감보다 먼저여야 한다. 뒤에 있으면 여기서 9가 나온다.
        assertThat(남은수량(sale.getId())).isEqualTo(10);
    }

    private long 개수(List<Throwable> failures, ErrorCode code) {
        return failures.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .filter(e -> e.getErrorCode() == code)
                .count();
    }
}
