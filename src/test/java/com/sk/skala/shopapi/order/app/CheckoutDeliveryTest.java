package com.sk.skala.shopapi.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.delivery.app.DeliveryAddressService;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressRequest;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressResponse;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.order.dto.CheckoutRequest;
import com.sk.skala.shopapi.order.ledger.Order;
import com.sk.skala.shopapi.order.ledger.OrderRepository;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * 주문서에서 고른 배송지가 실제로 그 주문에 쓰이는지 확인한다. (D42)
 *
 * <p>이 테스트를 따로 만든 이유가 있다. 배송지가 하나뿐일 때는 원장이
 * <b>"기본 배송지"를 스스로 찾아 쓰는 것</b>으로 충분했다. 여러 개를 둘 수 있게 되면서
 * 그 가정이 깨졌는데, <b>기존 테스트 224개가 전부 통과했다.</b>
 * 배송지를 하나만 만드는 테스트에서는 "고른 것"과 "기본"이 늘 같기 때문이다.
 *
 * <p>즉 회사 주소로 주문했는데 원장에는 집 주소가 남는 상태를
 * 초록불이 가려주고 있었다. 그래서 <b>배송지를 두 개 이상 두고</b> 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("주문서에서 고른 배송지")
class CheckoutDeliveryTest {

    /** V8 시드의 이벤트 행이 상품을 참조한다. 상품보다 먼저 지워야 한다. */
    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private DeliveryAddressService deliveryAddressService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private static final String 고객아이디 = "skala01";

    private Product 립스틱;
    private Long 집Id;
    private Long 회사Id;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        flashSaleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        customerRepository.save(new Customer(고객아이디, "$2a$10$h", Money.of(1_000_000)));
        립스틱 = productRepository.save(
                new Product("벨벳 매트 립스틱 #로즈브릭", Money.of(21_000), 100));

        // 첫 배송지가 자동으로 기본이 된다. 그래서 '집'이 기본, '회사'는 아니다.
        집Id = 등록("집", "10101", "서울시 강남구 테헤란로 1", "1234*");
        회사Id = 등록("회사", "20202", "서울시 종로구 세종대로 2", null);
    }

    private Long 등록(String label, String zipcode, String address, String entrancePassword) {
        DeliveryAddressResponse saved = deliveryAddressService.add(고객아이디,
                new DeliveryAddressRequest(label, "신민서", "01012345678",
                        zipcode, address, "3층", entrancePassword, null));
        return saved.id();
    }

    private CheckoutRequest 주문(Long addressId, DeliveryAddressRequest 새배송지) {
        return new CheckoutRequest(
                List.of(new CheckoutRequest.Item(립스틱.getId(), 1)),
                PaymentMethod.POINT, 0L, null, addressId, 새배송지);
    }

    private Order 마지막주문() {
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        return orders.get(0);
    }

    @Test
    @DisplayName("기본이 아닌 배송지를 고르면 그 배송지로 기록된다")
    void 고른배송지가기록된다() {
        checkoutService.checkout(고객아이디, 주문(회사Id, null));

        // 예전 구현은 여기서 '집'이 나왔다. 원장이 기본 배송지를 스스로 찾았기 때문이다.
        assertThat(마지막주문().getAddress()).contains("세종대로");
    }

    @Test
    @DisplayName("아무것도 고르지 않으면 기본 배송지로 기록된다")
    void 지정하지않으면기본() {
        checkoutService.checkout(고객아이디, 주문(null, null));

        assertThat(마지막주문().getAddress()).contains("테헤란로");
    }

    @Test
    @DisplayName("저장된 배송지를 고르면 사본이 생기지 않는다")
    void 사본이생기지않는다() {
        checkoutService.checkout(고객아이디, 주문(회사Id, null));

        // 고른 것을 다시 저장하면 같은 주소가 하나 더 쌓인다.
        // 주문할 때마다 목록이 길어지면 사용자가 고를 수 없게 된다.
        assertThat(deliveryAddressService.findAll(고객아이디)).hasSize(2);
    }

    @Test
    @DisplayName("저장된 배송지를 골라도 공동현관 비밀번호가 지워지지 않는다")
    void 공동현관비밀번호가유지된다() {
        // 목록 응답에는 비밀번호 값이 들어 있지 않다(등록 여부만 온다).
        // 화면이 고른 배송지의 '내용'을 되보내는 구조였다면 빈 값으로 덮였을 것이다.
        checkoutService.checkout(고객아이디, 주문(집Id, null));

        DeliveryAddressResponse 집 = deliveryAddressService.findAll(고객아이디).stream()
                .filter(a -> a.id().equals(집Id))
                .findFirst()
                .orElseThrow();

        assertThat(집.hasEntrancePassword()).isTrue();
    }

    @Test
    @DisplayName("새 배송지를 입력하면 저장되고 그 배송지로 기록된다")
    void 새배송지를입력하면저장된다() {
        checkoutService.checkout(고객아이디, 주문(null,
                new DeliveryAddressRequest("본가", "신민서", "01012345678", "30303",
                        "부산시 해운대구 해운대로 3", "101동 202호", "9999", null)));

        assertThat(deliveryAddressService.findAll(고객아이디)).hasSize(3);
        assertThat(마지막주문().getAddress()).contains("해운대로");
    }

    @Test
    @DisplayName("남의 배송지 id를 넣으면 거절된다")
    void 남의배송지는쓸수없다() {
        customerRepository.save(new Customer("skala02", "$2a$10$h", Money.of(1_000_000)));

        // 경로에 고객 아이디가 없어도 토큰 주체로만 조회하므로,
        // 남의 배송지 id를 알아내도 쓸 수 없어야 한다. (D6)
        assertThatThrownBy(() -> checkoutService.checkout("skala02", 주문(집Id, null)))
                .isInstanceOf(BusinessException.class);
    }
}
