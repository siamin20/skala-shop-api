package com.sk.skala.shopapi.order.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
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
 * 주문 API 통합 테스트.
 *
 * <p>업무 규칙은 {@code OrderServiceTest}가 확인한다. 여기서는 <b>HTTP 경계</b>가 대상이다.
 * 특히 <b>주문 주체가 토큰에서만 오는가</b>를 확인한다(D6). 이 성질이 깨지면
 * 남의 아이디로 주문할 수 있게 되므로 가장 중요한 검증이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Long 무선마우스Id;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        customerRepository.save(new Customer("skala01", "$2a$10$h", Money.of(1_000_000)));
        customerRepository.save(new Customer("skala02", "$2a$10$h", Money.of(1_000_000)));
        무선마우스Id = productRepository.save(new Product("무선마우스", Money.of(15_000))).getId();
    }

    private String orderBody(int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(무선마우스Id, quantity);
    }

    @Nested
    @DisplayName("주문과 취소")
    @WithMockCustomer("skala01")
    class PlaceAndCancel {

        @Test
        @DisplayName("주문하면 포인트가 차감되고 변경 후 전체 상태를 돌려준다")
        void placeOrder() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value("skala01"))
                    .andExpect(jsonPath("$.point").value(970_000))
                    .andExpect(jsonPath("$.products[0].quantity").value(2))
                    .andExpect(jsonPath("$.totalSpent").value(30_000));
        }

        @Test
        @DisplayName("취소하면 포인트가 환급된다")
        void cancelOrder() throws Exception {
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON).content(orderBody(2)));

            mockMvc.perform(post("/api/orders/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.point").value(985_000));
        }

        @Test
        @DisplayName("내 주문 목록을 조회한다")
        void getMyOrders() throws Exception {
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON).content(orderBody(1)));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value("skala01"))
                    .andExpect(jsonPath("$.products[0].productName").value("무선마우스"));
        }

        @Test
        @DisplayName("포인트가 부족하면 409")
        void insufficientPoint() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(100)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));
        }

        @Test
        @DisplayName("없는 상품은 404")
        void productNotFound() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":9999,\"quantity\":1}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        }

        @Test
        @DisplayName("수량 0 이하는 400")
        void rejectNonPositiveQuantity() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(0)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.quantity").exists());
        }
    }

    @Nested
    @DisplayName("주문 주체는 토큰에서만 온다")
    class SubjectComesFromToken {

        @Test
        @DisplayName("본문에 customerId를 넣어도 무시되고 토큰 주체로 주문된다")
        @WithMockCustomer("skala01")
        void ignoresCustomerIdInBody() throws Exception {
            // OrderRequest에 customerId 필드가 없으므로 Jackson이 무시한다.
            // 만약 필드가 생기면 이 테스트가 깨져 남의 아이디로 주문할 수 있게 됐음을 알린다.
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"skala02\",\"productId\":%d,\"quantity\":1}"
                                    .formatted(무선마우스Id)))
                    .andExpect(status().isOk())
                    // skala02가 아니라 토큰 주체인 skala01이 차감돼야 한다
                    .andExpect(jsonPath("$.customerId").value("skala01"))
                    .andExpect(jsonPath("$.point").value(985_000));
        }

        @Test
        @DisplayName("다른 고객의 잔액은 그대로다")
        @WithMockCustomer("skala01")
        void otherCustomerUnaffected() throws Exception {
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"skala02\",\"productId\":%d,\"quantity\":1}"
                            .formatted(무선마우스Id)));

            org.assertj.core.api.Assertions
                    .assertThat(customerRepository.findById("skala02").orElseThrow().getPoint())
                    .isEqualTo(Money.of(1_000_000));
        }

        @Test
        @DisplayName("비로그인은 401")
        @WithAnonymousUser
        void requiresLogin() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(1)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        }
    }
}
