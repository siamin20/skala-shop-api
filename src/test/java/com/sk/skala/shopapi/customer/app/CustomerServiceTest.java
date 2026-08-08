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
import com.sk.skala.shopapi.customer.dto.PointUpdateRequest;
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
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jakarta.validation.Validator validator;

    @BeforeEach
    void setUp() {
        // 참조하는 쪽부터 지운다. 반대로 하면 외래 키 제약에 걸린다.
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        // 상품을 참조하는 쪽을 먼저 지운다. 순서를 뒤집으면 외래 키에 걸린다.
        flashSaleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    private Customer 가입고객(String id, long point) {
        return customerRepository.save(
                new Customer(id, passwordEncoder.encode("pw123456"), Money.of(point)));
    }

    /**
     * 회원가입 검증.
     *
     * <p>초기 포인트 지급과 아이디 중복 차단이 대상이지만, 실제 무게는 <b>비밀번호 해싱</b>에 있다.
     * 해싱을 빠뜨려도 다른 테스트는 전부 통과하므로 이 묶음이 유일한 방어선이다.
     */
    @Nested
    @DisplayName("회원가입")
    class SignUp {

        @Test
        @DisplayName("가입하면 설정된 초기 포인트를 받는다")
        void signUpGrantsInitialPoint() {
            CustomerResponse created = customerService.signUp(
                    new SignUpRequest("skala01", "pw123456"));

            assertThat(created.customerId()).isEqualTo("skala01");
            // 설정값(shop.signup.initial-point)을 그대로 확인한다.
            // 숫자를 못 박으면 정책이 바뀔 때마다 테스트를 고쳐야 하지만,
            // 그렇다고 설정을 읽어와 비교하면 "설정이 반영되는가"를 검증하지 못한다.
            // 뷰티 커머스로 주제를 바꾸며 100만원에서 3만원으로 낮췄다. (D29)
            assertThat(created.point()).isEqualTo(30_000);
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
        @DisplayName("BCrypt가 반영하지 못하는 길이의 비밀번호는 검증에서 걸러진다")
        void rejectPasswordOverBcryptLimit() {
            // 한글 64자 = UTF-8 192바이트. @Size(max = 64)는 문자 수만 세므로 통과하지만
            // BCrypt는 72바이트에서 잘라내, 앞 24자가 같은 다른 비밀번호와 같은 해시가 된다.
            String 한글64자 = "가".repeat(64);

            assertThat(한글64자.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .isEqualTo(192);
            assertThat(validator.validate(new SignUpRequest("skala01", 한글64자)))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("72바이트 이내면 한글 비밀번호도 통과한다")
        void allowMultibytePasswordWithinLimit() {
            // 한글 24자 = 72바이트. 경계값이 막히지 않는지 확인한다.
            assertThat(validator.validate(new SignUpRequest("skala01", "가".repeat(24))))
                    .isEmpty();
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

    /**
     * 조회 검증.
     *
     * <p>고객 정보와 주문 목록을 한 응답으로 묶는 경로를 확인한다.
     * 주문이 없는 경우를 따로 두는 이유는, 빈 목록에서 합계가 0이 아니라
     * 예외로 터지는 실수가 흔하기 때문이다.
     */
    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        @DisplayName("고객 정보와 주문 목록을 함께 준다")
        void getCustomerWithOrders() {
            Customer customer = 가입고객("skala01", 970_000);
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000), 1000));
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

    /**
     * 포인트 충전 검증.
     *
     * <p>명세의 "덮어쓰기"를 "누적"으로 바꾼 결정(D13)이 실제로 지켜지는지 고정한다.
     * 덮어쓰기로 되돌아가면 잔액이 충전액과 같아지므로 첫 테스트가 바로 깨진다.
     */
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

    /**
     * 포인트 조정 검증. (관리자 전용, 명세 552p)
     *
     * <p>충전과의 차이를 고정한다. 조정은 이전 잔액을 무시하고 덮어쓰므로 <b>멱등</b>하고,
     * 충전은 보낼 때마다 더해지므로 멱등하지 않다. 둘을 같은 동작으로 합치면
     * 한쪽은 잔액 조작 구멍이 되고 다른 쪽은 중복 충전 위험이 된다(D13).
     */
    @Nested
    @DisplayName("포인트 조정")
    class UpdatePoint {

        @Test
        @DisplayName("이전 잔액을 무시하고 지정한 값으로 덮어쓴다")
        void adjustOverwritesBalance() {
            가입고객("skala01", 10_000);

            CustomerResponse updated = customerService.updateCustomerPoint(
                    "skala01", new PointUpdateRequest(5_000L));

            // 충전이라면 15,000이 된다. 조정이므로 5,000이어야 한다.
            assertThat(updated.point()).isEqualTo(5_000);
        }

        @Test
        @DisplayName("여러 번 호출해도 결과가 같다")
        void isIdempotent() {
            가입고객("skala01", 10_000);

            customerService.updateCustomerPoint("skala01", new PointUpdateRequest(7_000L));
            customerService.updateCustomerPoint("skala01", new PointUpdateRequest(7_000L));
            CustomerResponse third = customerService.updateCustomerPoint(
                    "skala01", new PointUpdateRequest(7_000L));

            // 멱등성. 충전은 세 번 부르면 세 배가 되지만 조정은 그대로다.
            assertThat(third.point()).isEqualTo(7_000);
        }

        @Test
        @DisplayName("0으로 초기화할 수 있다")
        void allowZero() {
            가입고객("skala01", 10_000);

            CustomerResponse updated = customerService.updateCustomerPoint(
                    "skala01", new PointUpdateRequest(0L));

            assertThat(updated.point()).isZero();
        }

        @Test
        @DisplayName("음수는 요청 검증에서 걸러진다")
        void rejectNegative() {
            assertThat(validator.validate(new PointUpdateRequest(-1L))).isNotEmpty();
        }

        @Test
        @DisplayName("없는 고객은 DATA_NOT_FOUND")
        void updateNotFound() {
            assertThatThrownBy(() -> customerService.updateCustomerPoint(
                    "nobody", new PointUpdateRequest(5_000L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }

    /**
     * 탈퇴 검증.
     *
     * <p>외래 키 제약 때문에 주문 항목을 먼저 지워야 한다는 점, 그리고 고객이 사라져도
     * 상품은 남아야 한다는 점을 확인한다. 삭제 범위를 잘못 잡으면 다른 고객의 주문까지
     * 참조하는 상품이 함께 사라진다.
     */
    @Nested
    @DisplayName("탈퇴")
    class Delete {

        @Test
        @DisplayName("주문이 있어도 탈퇴할 수 있다")
        void deleteWithOrders() {
            Customer customer = 가입고객("skala01", 1_000_000);
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000), 1000));
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
            Product mouse = productRepository.save(new Product("무선마우스", Money.of(15_000), 1000));
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
