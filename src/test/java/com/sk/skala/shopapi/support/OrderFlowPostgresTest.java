package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;

/**
 * 주문 흐름을 실제 PostgreSQL 위에서 한 번 더 돌린다. (D21)
 *
 * <p>같은 시나리오를 {@code OrderControllerTest}가 H2로 이미 검증한다. 그런데도 중복해서
 * 두는 이유는, <b>H2에서 초록불인 것이 PostgreSQL에서도 그렇다는 보장이 없다</b>는 것을
 * 이 프로젝트에서 이미 두 번 겪었기 때문이다.
 *
 * <ol>
 *   <li>{@code CLOB} 타입 — PostgreSQL에 없어 마이그레이션이 실패했다
 *   <li>{@code @Lob String} — PostgreSQL에서 Large Object로 매핑돼 validate가 막았다
 * </ol>
 *
 * <p>둘 다 <b>기동 단계</b>에서 드러난 문제라 {@link MigrationCompatibilityTest}만으로 잡힌다.
 * 이 클래스가 맡는 것은 그 다음이다. 기동에 성공한 뒤 <b>값을 실제로 쓰고 읽는 경로</b>에서
 * 두 DB가 다르게 동작하는지 본다. 정수 연산, 유니크 제약을 통한 수량 누적,
 * 그리고 문제가 났던 응답 본문 저장이 그 대상이다.
 *
 * <p>전부 복제하지는 않는다. 업무 규칙 자체는 DB와 무관하므로 H2 테스트가 담당하고,
 * 여기서는 <b>DB에 닿는 경로</b>만 고른다. 다 복제하면 컨테이너 위에서 도는 테스트가
 * 두 배로 늘어 실행 시간만 길어진다.
 */
@AutoConfigureMockMvc
@Transactional
@WithMockCustomer("pgtest01")
@DisplayName("PostgreSQL 주문 흐름")
class OrderFlowPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long 무선마우스Id;

    @BeforeEach
    void setUp() {
        // 상품은 V2 마이그레이션이 넣어둔 것을 그대로 쓴다. 지우고 다시 넣으면
        // 컨테이너를 공유하는 다른 테스트가 영향을 받는다.
        무선마우스Id = jdbc.queryForObject(
                "SELECT id FROM product WHERE product_name = '무선마우스'", Long.class);

        customerRepository.save(new Customer("pgtest01", "$2a$10$h", Money.of(1_000_000)));
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private String body(int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(무선마우스Id, quantity);
    }

    @Test
    @DisplayName("주문하면 포인트가 정확히 차감된다")
    void placeOrder() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(body(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(955_000))
                .andExpect(jsonPath("$.totalSpent").value(45_000));

        // 값을 SQL로 직접 확인한다. JPA를 거치면 영속성 컨텍스트의 값을 볼 수도 있어
        // "DB에 실제로 무엇이 들어갔는가"를 보장하지 못한다.
        Long point = jdbc.queryForObject(
                "SELECT customer_point FROM customer WHERE customer_id = 'pgtest01'", Long.class);
        assertThat(point).isEqualTo(955_000L);
    }

    @Test
    @DisplayName("같은 상품을 다시 주문하면 행이 늘지 않고 수량이 누적된다")
    void reorderAccumulates() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body(2)));
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body(3)));

        // (customer_id, product_id) 유니크 제약이 이 동작의 근거다.
        // 제약이 PostgreSQL에서 제대로 걸리지 않았다면 여기서 행이 2개가 된다.
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_item WHERE customer_id = 'pgtest01'", Integer.class);
        assertThat(rows).isEqualTo(1);

        Integer quantity = jdbc.queryForObject(
                "SELECT quantity FROM order_item WHERE customer_id = 'pgtest01'", Integer.class);
        assertThat(quantity).isEqualTo(5);
    }

    @Test
    @DisplayName("부분 취소하면 결제한 만큼만 환급된다")
    void partialCancelRefundsProportionally() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(body(3)));

        mockMvc.perform(post("/api/orders/cancel").header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isOk())
                // 45,000원 중 1개분 15,000원만 돌아온다
                .andExpect(jsonPath("$.point").value(970_000));

        Long total = jdbc.queryForObject(
                "SELECT total_amount FROM order_item WHERE customer_id = 'pgtest01'", Long.class);
        // 금액은 BIGINT다. 실수형이었다면 여기서 오차가 드러난다. (D1)
        assertThat(total).isEqualTo(30_000L);
    }

    /**
     * 문제가 났던 컬럼을 실제로 쓰고 읽는다.
     *
     * <p>{@code response_body}가 Large Object로 매핑돼 있었다면, 기동이 성공하더라도
     * 이 경로에서 값을 넣거나 꺼낼 때 문제가 난다. 저장된 응답을 그대로 돌려받는지
     * 확인하는 것이 곧 컬럼이 제대로 동작하는지 확인하는 것이다.
     */
    @Test
    @DisplayName("저장된 응답 본문이 PostgreSQL을 왕복해도 그대로다")
    void idempotentResponseSurvivesRoundTrip() throws Exception {
        String k = key();

        String first = mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 두 번째는 작업을 실행하지 않고 DB에 저장해둔 본문을 꺼내 돌려준다.
        String replayed = mockMvc.perform(post("/api/orders").header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isOk())
                // 인코딩을 명시하지 않으면 MockMvc가 ISO-8859-1로 디코딩해 한글이 깨진다.
                // 서버가 잘못 보낸 것이 아니라 테스트가 잘못 읽는 것이다. 실제 응답 헤더는
                // application/json이고 클라이언트는 UTF-8로 해석한다.
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(replayed).isEqualTo(first);

        // 한글 상품명이 들어 있는 JSON이다. 인코딩이 어긋나면 여기서 드러난다.
        assertThat(replayed).contains("무선마우스");

        // 컬럼에 번호가 아니라 본문 자체가 들어 있어야 한다.
        // Large Object로 저장됐다면 여기에 pg_largeobject를 가리키는 숫자만 남는다.
        String stored = jdbc.queryForObject(
                "SELECT response_body FROM idempotency_key WHERE idempotency_key = ?",
                String.class, k);
        assertThat(stored).isEqualTo(first);
    }
}
