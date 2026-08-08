package com.sk.skala.shopapi.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;
import com.sk.skala.shopapi.support.WithMockCustomer;

/**
 * 멱등성 검증.
 *
 * <p>확인하려는 것은 하나다. <b>같은 요청이 두 번 들어와도 부작용은 한 번만 일어나는가.</b>
 *
 * <p>이 보장이 없으면 클라이언트가 타임아웃 후 재시도할 때 포인트가 두 번 차감되거나
 * 두 번 충전된다. 서버는 요청 내용만으로 재시도와 새 요청을 구분할 수 없기 때문에,
 * 클라이언트가 붙여 보내는 키가 유일한 단서다. (D20)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockCustomer("skala01")
class IdempotencyTest {

    /**
     * 선착순 이벤트 저장소.
     *
     * <p>이 테스트가 이벤트를 쓰지는 않는다. 그런데 V8 시드가 넣은 이벤트 행이
     * 상품을 외래 키로 참조하기 때문에, 상품을 지우기 전에 이벤트부터 지워야 한다.
     * 순서를 지키지 않으면 제약 위반으로 setUp 자체가 실패한다.
     */
    @Autowired
    private com.sk.skala.shopapi.event.domain.FlashSaleRepository flashSaleRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private Long 무선마우스Id;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        // 상품을 참조하는 쪽을 먼저 지운다. 순서를 뒤집으면 외래 키에 걸린다.
        flashSaleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        customerRepository.save(new Customer("skala01", "$2a$10$h", Money.of(1_000_000)));
        무선마우스Id = productRepository.save(new Product("무선마우스", Money.of(15_000), 1000)).getId();
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private Money 잔액() {
        return customerRepository.findById("skala01").orElseThrow().getPoint();
    }

    @Nested
    @DisplayName("주문")
    class Order {

        private String body(int quantity) {
            return "{\"productId\":%d,\"quantity\":%d}".formatted(무선마우스Id, quantity);
        }

        @Test
        @DisplayName("같은 키로 두 번 주문해도 포인트는 한 번만 차감된다")
        void sameKeyDeductsOnce() throws Exception {
            String k = key();

            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.point").value(970_000));

            // 네트워크가 끊겨 응답을 못 받은 클라이언트가 같은 요청을 재시도한 상황
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                    .andExpect(status().isOk())
                    // 멱등성이 없으면 940,000이 된다
                    .andExpect(jsonPath("$.point").value(970_000));

            assertThat(잔액()).isEqualTo(Money.of(970_000));
        }

        @Test
        @DisplayName("재시도해도 주문 수량이 누적되지 않는다")
        void sameKeyDoesNotAccumulateQuantity() throws Exception {
            String k = key();

            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                    .contentType(MediaType.APPLICATION_JSON).content(body(2)));
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                    .andExpect(jsonPath("$.products[0].quantity").value(2));
        }

        @Test
        @DisplayName("다른 키면 새 주문으로 처리된다")
        void differentKeyExecutesAgain() throws Exception {
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                    .contentType(MediaType.APPLICATION_JSON).content(body(1)));

            // 사용자가 정말로 하나 더 주문한 경우다. 막으면 안 된다.
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                    .andExpect(jsonPath("$.point").value(970_000))
                    .andExpect(jsonPath("$.products[0].quantity").value(2));
        }

        @Test
        @DisplayName("같은 키로 다른 내용을 보내면 거부한다")
        void rejectSameKeyDifferentRequest() throws Exception {
            String k = key();

            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                    .contentType(MediaType.APPLICATION_JSON).content(body(1)));

            // 막지 않으면 "1개 주문"에 쓴 키로 "10개 주문"을 보내
            // 실행되지 않은 채 성공 응답만 받아낼 수 있다.
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(10)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        }

        @Test
        @DisplayName("키 헤더가 없으면 400")
        void requireKeyHeader() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패한 요청은 기억하지 않아 재시도할 수 있다")
        void failedRequestIsNotMemoized() throws Exception {
            String k = key();

            // 잔액을 넘는 주문이라 실패한다. 재고(1000)는 넘지 않는 수량을 고른다.
            //
            // 재고까지 넘기면 OUT_OF_STOCK이 먼저 나서 이 테스트가 확인하려는
            // "잔액 부족으로 실패한 요청"이 아니게 된다. 게다가 이 테스트는 @Transactional이라
            // MockMvc 호출들이 한 트랜잭션을 공유한다. 예외가 나도 실제 롤백은 테스트가
            // 끝날 때 일어나므로, 앞선 호출이 영속성 컨텍스트에 남긴 재고 차감이 그대로 보인다.
            // 운영에서는 요청마다 트랜잭션이 따로라 생기지 않는 상황이다.
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(100)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));

            // 실패를 기억하면 포인트를 충전한 뒤에도 영원히 막힌다.
            // 작업과 키 저장이 같은 트랜잭션이라 함께 롤백되어야 한다.
            assertThat(idempotencyKeyRepository.findById(k)).isEmpty();

            mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        private String body(int quantity) {
            return "{\"productId\":%d,\"quantity\":%d}".formatted(무선마우스Id, quantity);
        }

        @Test
        @DisplayName("같은 키로 두 번 취소해도 한 번만 환급된다")
        void sameKeyRefundsOnce() throws Exception {
            mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                    .contentType(MediaType.APPLICATION_JSON).content(body(2)));

            String k = key();
            mockMvc.perform(post("/api/orders/cancel").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                    .andExpect(jsonPath("$.point").value(985_000));

            mockMvc.perform(post("/api/orders/cancel").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                    // 멱등성이 없으면 1,000,000이 되어 공짜로 상품을 갖게 된다
                    .andExpect(jsonPath("$.point").value(985_000));

            assertThat(잔액()).isEqualTo(Money.of(985_000));
        }
    }

    @Nested
    @DisplayName("포인트 충전")
    class Charge {

        @Test
        @DisplayName("같은 키로 두 번 충전해도 한 번만 반영된다")
        void sameKeyChargesOnce() throws Exception {
            String k = key();
            String body = "{\"amount\":50000}";

            mockMvc.perform(post("/api/customers/skala01/points").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.point").value(1_050_000));

            mockMvc.perform(post("/api/customers/skala01/points").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    // 멱등성이 없으면 1,100,000이 된다
                    .andExpect(jsonPath("$.point").value(1_050_000));

            assertThat(잔액()).isEqualTo(Money.of(1_050_000));
        }

        @Test
        @DisplayName("같은 키로 다른 금액을 보내면 거부한다")
        void rejectDifferentAmount() throws Exception {
            String k = key();

            mockMvc.perform(post("/api/customers/skala01/points").header("Idempotency-Key", k)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5000}"));

            mockMvc.perform(post("/api/customers/skala01/points").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

            // 거부되었으므로 잔액은 첫 충전 결과 그대로여야 한다
            assertThat(잔액()).isEqualTo(Money.of(1_005_000));
        }
    }

    @Nested
    @DisplayName("키 소유권")
    class Ownership {

        @Test
        @DisplayName("남이 쓴 키를 재사용하면 거부한다")
        @WithMockCustomer("skala02")
        void rejectOthersKey() throws Exception {
            customerRepository.save(new Customer("skala02", "$2a$10$h", Money.of(1_000_000)));

            String k = key();
            String body = "{\"amount\":5000}";

            // skala02가 자기 키로 충전
            mockMvc.perform(post("/api/customers/skala02/points").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            // 같은 키를 다시 쓰면 소유자가 맞으므로 저장된 응답이 나온다
            mockMvc.perform(post("/api/customers/skala02/points").header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.point").value(1_005_000));
        }
    }
}
