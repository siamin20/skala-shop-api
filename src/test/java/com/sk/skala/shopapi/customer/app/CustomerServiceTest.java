package com.sk.skala.shopapi.customer.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.dto.PointChargeRequest;
import com.sk.skala.shopapi.customer.dto.SignUpRequest;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.OrderItem;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * {@link CustomerService} 통합 테스트.
 *
 * <p>가장 중요하게 확인하는 것은 <b>비밀번호가 평문으로 저장되지 않는다</b>는 점이다.
 * 이 검증이 없으면 해싱을 빠뜨려도 나머지 테스트는 전부 통과한다.
 */
@SpringBootTest
@Transactional
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // 참조하는 쪽부터 지운다. 반대로 하면 외래 키 제약에 걸린다.
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    private Customer 가입고객(String id, long point) {
        return customerRepository.save(
                new Customer(id, passwordEncoder.encode("pw123456"), Money.of(point)));
    }

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        @Test
        @DisplayName("가입하면 설정된 초기 포인트를 받는다")
        void signUpGrantsInitialPoint() {
            CustomerResponse created = customerService.signUp(
                    new SignUpRequest("skala01", "pw123456"));

            assertThat(created.customerId()).isEqualTo("skala01");
            assertThat(created.point()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("비밀번호는 평문으로 저장되지 않는다")
        void passwordIsHashed() {
            customerService.signUp(new SignUpRequest("skala01", "pw123456"));

            Customer saved = customerRepository.findById("skala01").orElseThrow();

            assertThat(saved.getPassword())
                    .isNotEqualTo("pw123456")
                    .startsWith("$2");  // BCrypt 해시 접두사
            assertThat(passwordEncoder.matches("pw123456", saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("같은 비밀번호라도 계정마다 다른 해시가 저장된다")
        void sameSaltIsNotReused() {
            // BCrypt는 해시마다 무작위 솔트를 섞는다. 솔트가 없으면 같은 비밀번호가
            // 같은 해시가 되어, 하나가 뚫리면 같은 비밀번호를 쓰는 계정이 전부 뚫린다.
            customerService.signUp(new SignUpRequest("skala01", "pw123456"));
            customerService.signUp(new SignUpRequest("skala02", "pw123456"));

            String first = customerRepository.findById("skala01").orElseThrow().getPassword();
            String second = customerRepository.findById("skala02").orElseThrow().getPassword();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("아이디가 중복이면 DATA_DUPLICATED")
        void rejectDuplicateId() {
            가입고객("skala01", 0);

            assertThatThrownBy(() -> customerService.signUp(new SignUpRequest("skala01", "pw123456")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        @DisplayName("고객 정보와 주문 목록을 함께 준다")
        void getCustomerWithOrders() {
            Customer customer = 가입고객("skala01", 970_000);
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000)));
            orderItemRepository.save(new OrderItem(customer, mouse, 2));

            OrderListResponse response = customerService.getCustomerWithOrders("skala01");

            assertThat(response.customerId()).isEqualTo("skala01");
            assertThat(response.point()).isEqualTo(970_000);
            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).productName()).isEqualTo("무선마우스");
            assertThat(response.products().get(0).quantity()).isEqualTo(2);
            assertThat(response.products().get(0).totalPrice()).isEqualTo(30_000);
            assertThat(response.totalSpent()).isEqualTo(30_000);
        }

        @Test
        @DisplayName("주문이 없으면 빈 목록을 준다")
        void emptyOrderList() {
            가입고객("skala01", 1_000_000);

            OrderListResponse response = customerService.getCustomerWithOrders("skala01");

            assertThat(response.products()).isEmpty();
            assertThat(response.totalSpent()).isZero();
        }

        @Test
        @DisplayName("없는 고객은 DATA_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> customerService.getCustomerWithOrders("nobody"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }

        @Test
        @DisplayName("목록을 페이지 단위로 조회한다")
        void getCustomersByPage() {
            가입고객("skala01", 0);
            가입고객("skala02", 0);
            가입고객("skala03", 0);

            var page = customerService.getCustomers(0, 2);

            assertThat(page.content()).hasSize(2);
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.last()).isFalse();
        }
    }

    @Nested
    @DisplayName("포인트 충전")
    class ChargePoint {

        @Test
        @DisplayName("충전하면 기존 잔액에 더해진다")
        void chargeAddsToBalance() {
            가입고객("skala01", 10_000);

            CustomerResponse charged = customerService.chargePoint(
                    "skala01", new PointChargeRequest(5_000L));

            // 덮어쓰기라면 5,000이 된다. 더하기이므로 15,000이어야 한다.
            assertThat(charged.point()).isEqualTo(15_000);
        }

        @Test
        @DisplayName("없는 고객은 DATA_NOT_FOUND")
        void chargeNotFound() {
            assertThatThrownBy(() -> customerService.chargePoint(
                    "nobody", new PointChargeRequest(5_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Delete {

        @Test
        @DisplayName("주문이 있어도 탈퇴할 수 있다")
        void deleteWithOrders() {
            Customer customer = 가입고객("skala01", 1_000_000);
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000)));
            orderItemRepository.save(new OrderItem(customer, mouse, 2));

            // 주문 항목을 먼저 지우지 않으면 외래 키 제약에 걸려 실패한다
            customerService.deleteCustomer("skala01");

            assertThat(customerRepository.findById("skala01")).isEmpty();
            assertThat(orderItemRepository.findByCustomer_CustomerId("skala01")).isEmpty();
        }

        @Test
        @DisplayName("탈퇴해도 상품은 남는다")
        void productSurvivesCustomerDeletion() {
            Customer customer = 가입고객("skala01", 1_000_000);
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000)));
            orderItemRepository.save(new OrderItem(customer, mouse, 1));

            customerService.deleteCustomer("skala01");

            assertThat(productRepository.findById(mouse.getId())).isPresent();
        }

        @Test
        @DisplayName("없는 고객은 DATA_NOT_FOUND")
        void deleteNotFound() {
            assertThatThrownBy(() -> customerService.deleteCustomer("nobody"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }
}
