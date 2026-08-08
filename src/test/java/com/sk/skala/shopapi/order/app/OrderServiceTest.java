package com.sk.skala.shopapi.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.app.ProductService;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * {@link OrderService} 통합 테스트.
 *
 * <p>과제 명세의 핵심 시나리오(530p)를 그대로 재현한다.
 * 1,000,000 포인트로 15,000원 상품 2개를 주문하면 970,000이 남고,
 * 1개를 취소하면 985,000으로 돌아온다.
 *
 * <p>여기서 확인하는 것은 <b>포인트와 수량이 항상 짝이 맞는가</b>이다.
 * 둘 중 하나만 반영되면 공짜 주문이 생기거나 포인트가 증발한다.
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

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
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductService productService;

    private Customer 고객;
    private Product 무선마우스;

    @BeforeEach
    void setUp() {
        // 참조하는 쪽부터 지운다. 반대로 하면 외래 키 제약에 걸린다.
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        // 상품을 참조하는 쪽을 먼저 지운다. 순서를 뒤집으면 외래 키에 걸린다.
        flashSaleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        고객 = customerRepository.save(
                new Customer("skala01", "$2a$10$hashed", Money.of(1_000_000)));
        무선마우스 = productRepository.save(new Product("무선마우스", Money.of(15_000), 1000));
    }

    private Money 잔액() {
        return customerRepository.findById("skala01").orElseThrow().getPoint();
    }

    @Nested
    @DisplayName("주문")
    class PlaceOrder {

        @Test
        @DisplayName("명세 시나리오: 15,000원 2개를 주문하면 970,000이 남는다")
        void placeOrderDeductsPoint() {
            OrderListResponse response = orderService.placeOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 2));

            assertThat(response.point()).isEqualTo(970_000);
            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).quantity()).isEqualTo(2);
            assertThat(response.products().get(0).totalPrice()).isEqualTo(30_000);
            assertThat(response.totalSpent()).isEqualTo(30_000);
        }

        @Test
        @DisplayName("같은 상품을 다시 주문하면 새 행이 아니라 수량이 누적된다")
        void reorderAccumulatesQuantity() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));
            OrderListResponse response = orderService.placeOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 3));

            // 항목은 하나여야 한다. 둘이면 (고객, 상품) 유니크 제약도 깨진 것이다.
            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).quantity()).isEqualTo(5);
            // 기대값도 Money로 계산한다. 원시 타입 산술을 쓰면 운영 코드와 다른 계산 경로가 생긴다.
            Money 예상잔액 = Money.of(1_000_000).minus(Money.of(15_000).times(5));
            assertThat(response.point()).isEqualTo(예상잔액.getAmount());
        }

        @Test
        @DisplayName("다른 상품은 별도 항목으로 쌓인다")
        void differentProductsAreSeparateItems() {
            Product 허브 = productRepository.save(new Product("USB허브", Money.of(39_000), 1000));

            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));
            OrderListResponse response = orderService.placeOrder(
                    "skala01", new OrderRequest(허브.getId(), 1));

            assertThat(response.products()).hasSize(2);
            assertThat(response.totalSpent()).isEqualTo(54_000);
            assertThat(response.point()).isEqualTo(946_000);
        }

        @Test
        @DisplayName("포인트가 부족하면 주문이 거부되고 아무것도 남지 않는다")
        void rejectWhenInsufficientPoint() {
            Customer 가난한고객 = customerRepository.save(
                    new Customer("poor01", "$2a$10$hashed", Money.of(10_000)));

            assertThatThrownBy(() -> orderService.placeOrder(
                    "poor01", new OrderRequest(무선마우스.getId(), 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

            // 포인트도 그대로, 주문 항목도 생기지 않아야 한다
            assertThat(가난한고객.getPoint()).isEqualTo(Money.of(10_000));
            assertThat(orderItemRepository.findByCustomer_CustomerId("poor01")).isEmpty();
        }

        @Test
        @DisplayName("잔액과 정확히 같은 금액은 주문된다")
        void allowExactBalance() {
            Customer 딱맞는고객 = customerRepository.save(
                    new Customer("exact01", "$2a$10$hashed", Money.of(30_000)));

            OrderListResponse response = orderService.placeOrder(
                    "exact01", new OrderRequest(무선마우스.getId(), 2));

            assertThat(response.point()).isZero();
            assertThat(딱맞는고객.getPoint()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("없는 상품은 DATA_NOT_FOUND")
        void productNotFound() {
            assertThatThrownBy(() -> orderService.placeOrder(
                    "skala01", new OrderRequest(9999L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 고객은 DATA_NOT_FOUND")
        void customerNotFound() {
            assertThatThrownBy(() -> orderService.placeOrder(
                    "nobody", new OrderRequest(무선마우스.getId(), 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("취소")
    class CancelOrder {

        @Test
        @DisplayName("명세 시나리오: 2개 주문 후 1개 취소하면 985,000이 된다")
        void cancelRefundsPoint() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));

            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 1));

            assertThat(response.point()).isEqualTo(985_000);
            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("전량 취소하면 주문 항목이 사라지고 포인트가 온전히 돌아온다")
        void fullCancellationRemovesItem() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));

            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 2));

            // 수량 0인 행이 남으면 목록에 "0개 주문한 상품"이 보이고,
            // 유니크 제약 때문에 같은 상품을 다시 살 수도 없게 된다.
            assertThat(response.products()).isEmpty();
            assertThat(response.point()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("전량 취소 후 같은 상품을 다시 주문할 수 있다")
        void reorderAfterFullCancellation() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));
            orderService.cancelOrder("skala01", new OrderRequest(무선마우스.getId(), 1));

            OrderListResponse response = orderService.placeOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 1));

            assertThat(response.products()).hasSize(1);
            assertThat(response.point()).isEqualTo(985_000);
        }

        @Test
        @DisplayName("보유 수량보다 많이 취소하면 거부되고 포인트도 그대로다")
        void rejectOverCancellation() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));

            assertThatThrownBy(() -> orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 3)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);

            assertThat(잔액()).isEqualTo(Money.of(970_000));
        }

        @Test
        @DisplayName("주문한 적 없는 상품은 취소할 수 없다")
        void rejectCancellationWithoutOrder() {
            assertThatThrownBy(() -> orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_NOT_FOUND);
        }

        @Test
        @DisplayName("주문 후 가격이 올라도 환급액은 주문 시점 단가를 따른다")
        void refundUsesOrderTimePrice() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));

            // 주문 뒤 가격이 두 배로 오른 상황.
            // 현재 가격으로 환급하면 30,000원을 돌려줘 15,000원이 공짜로 생긴다.
            무선마우스.changePrice(Money.of(30_000));
            productRepository.save(무선마우스);

            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 1));

            assertThat(response.point()).isEqualTo(985_000);
        }
    }

    /**
     * 가격 변동과 환급 정합성.
     *
     * <p>차감은 현재 가격으로, 환급은 저장된 값으로 이뤄지므로 둘이 어긋나면 돈이 새거나 남는다.
     * 어느 방향이든 실제 금전 손실이라 가장 중요한 검증이다.
     */
    @Nested
    @DisplayName("가격 변동과 환급")
    class PriceChange {

        @Test
        @DisplayName("가격이 오른 뒤 재주문해도 전량 취소하면 결제액이 전부 돌아온다")
        void refundMatchesPaidAmountAfterPriceIncrease() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));   // -30,000

            무선마우스.changePrice(Money.of(30_000));
            productRepository.saveAndFlush(무선마우스);

            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));   // -30,000
            assertThat(잔액()).isEqualTo(Money.of(940_000));

            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 3));

            // 단가 스냅샷 방식이었다면 45,000원만 돌아와 985,000원이 되고, 고객이 15,000원을 잃는다.
            assertThat(response.point()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("가격이 내린 뒤 재주문해도 결제한 만큼만 돌아온다")
        void refundMatchesPaidAmountAfterPriceDecrease() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));   // -30,000

            무선마우스.changePrice(Money.of(5_000));
            productRepository.saveAndFlush(무선마우스);

            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));   // -10,000

            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 4));

            // 반대 방향도 확인한다. 어긋나면 이번엔 고객이 이득을 보고 서비스가 손실을 본다.
            assertThat(response.point()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("가격 변동 후 나눠 취소해도 잔액이 정확히 복구된다")
        void partialCancellationsRestoreExactBalance() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));

            무선마우스.changePrice(Money.of(30_000));
            productRepository.saveAndFlush(무선마우스);
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));

            orderService.cancelOrder("skala01", new OrderRequest(무선마우스.getId(), 1));
            orderService.cancelOrder("skala01", new OrderRequest(무선마우스.getId(), 1));
            OrderListResponse response = orderService.cancelOrder(
                    "skala01", new OrderRequest(무선마우스.getId(), 1));

            // 비례 계산에서 내림된 잔돈이 마지막 취소에서 정산된다
            assertThat(response.point()).isEqualTo(1_000_000);
            assertThat(response.products()).isEmpty();
        }
    }

    /**
     * 주문이 상품 삭제에 미치는 영향.
     *
     * <p>주문이 존재하면서 비로소 드러나는 경로다. 상품만 있을 때는 발생하지 않아
     * 상품 API 테스트만으로는 잡히지 않는다.
     */
    @Nested
    @DisplayName("주문된 상품 삭제")
    class DeleteOrderedProduct {

        @Test
        @DisplayName("주문 내역이 있는 상품은 삭제할 수 없고, 이유를 정확히 알려준다")
        void rejectDeletingOrderedProduct() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));

            // 이 검사가 없으면 외래 키 위반이 전역 처리기까지 올라가
            // DATA_DUPLICATED("이미 존재하는 데이터입니다")라는 엉뚱한 응답이 나간다.
            assertThatThrownBy(() -> productService.deleteProduct(무선마우스.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DATA_IN_USE);
        }

        @Test
        @DisplayName("주문을 전량 취소하면 상품을 삭제할 수 있다")
        void allowDeletingAfterFullCancellation() {
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 1));
            orderService.cancelOrder("skala01", new OrderRequest(무선마우스.getId(), 1));

            productService.deleteProduct(무선마우스.getId());

            assertThat(productRepository.findById(무선마우스.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("포인트와 수량의 정합성")
    class Consistency {

        @Test
        @DisplayName("주문과 취소를 섞어도 잔액이 정확하다")
        void mixedOperationsKeepBalanceExact() {
            Product 키보드 = productRepository.save(new Product("블루투스키보드", Money.of(29_000), 1000));

            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 2));   // -30,000
            orderService.placeOrder("skala01", new OrderRequest(키보드.getId(), 1));       // -29,000
            orderService.cancelOrder("skala01", new OrderRequest(무선마우스.getId(), 1));  // +15,000
            orderService.placeOrder("skala01", new OrderRequest(무선마우스.getId(), 3));   // -45,000

            OrderListResponse response = orderService.placeOrder(
                    "skala01", new OrderRequest(키보드.getId(), 1));                          // -29,000

            Money 예상잔액 = Money.of(1_000_000)
                    .minus(Money.of(30_000)).minus(Money.of(29_000))
                    .plus(Money.of(15_000))
                    .minus(Money.of(45_000)).minus(Money.of(29_000));

            assertThat(response.point()).isEqualTo(예상잔액.getAmount());

            // 주문 목록의 합계와 차감된 포인트가 일치해야 한다
            assertThat(response.totalSpent())
                    .isEqualTo(Money.of(1_000_000).minus(예상잔액).getAmount());
        }
    }
}
