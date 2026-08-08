package com.sk.skala.shopapi.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.order.domain.OrderItem;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.order.dto.CheckoutRequest;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.ledger.OrderRepository;
import com.sk.skala.shopapi.payment.dto.CardPaymentRequest;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * 여러 상품을 한 번에 결제할 때 적립금 사용액을 항목별로 어떻게 나누는가. (D46)
 *
 * <h2>왜 이 테스트가 생겼나</h2>
 *
 * <p>화면에서 주문하고 취소해보다가 <b>환급액이 이상한 것</b>을 발견했다.
 * 14,000원짜리를 취소했는데 4,550P가 돌아왔다. 계산이 맞지 않아 따라가 보니
 * 적립금을 <b>금액 비율이 아니라 항목 개수로 균등 분배</b>하고 있었다.
 *
 * <p>바로 위 주석은 "비율대로 나눠야 어느 항목을 취소하든 낸 만큼 돌아간다"고
 * 적혀 있었다. <b>의도는 주석에 있었고 코드가 그것을 하지 않았다.</b>
 *
 * <h2>무엇이 깨졌나</h2>
 *
 * <ol>
 *   <li><b>환급액이 틀린다.</b> 싼 항목을 취소하면 낸 것보다 많이 돌려받고,
 *       비싼 항목을 취소하면 적게 돌려받는다
 *   <li><b>금액이 맞지 않는다.</b> 배정된 몫이 그 상품 가격보다 크면 상품가로 잘리는데,
 *       잘린 금액이 다른 항목으로 재분배되지 않는다. 응답은 "포인트 20,000 사용"이라고
 *       하는데 실제로는 19,900만 차감된다. <b>고객이 100원을 덜 낸다</b>
 *   <li><b>원장과 어긋난다.</b> {@code order_line}은 금액 비율로 기록하는데
 *       {@code order_item}은 개수로 나눈 값을 갖는다. 같은 주문의 두 기록이 다르다
 * </ol>
 */
/*
 * 웹 서버를 실제로 띄운다.
 *
 * 카드 승인은 목업 카드사를 <b>HTTP로</b> 호출한다(D32). 같은 프로세스 안에 있어도
 * 경계를 실제로 통과시켜야 암호화·복호화가 검증되기 때문이다. 그래서 이 테스트도
 * 서버가 떠 있어야 하고, 게이트웨이가 그 주소를 보도록 issuer-url을 맞춰 준다.
 *
 * 카드 결제 경로를 타는 테스트가 <b>이 파일이 처음</b>이다. 그래서 아래 결함이
 * 화면에서 손으로 눌러 볼 때까지 드러나지 않았다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "server.port=18099",
            "shop.payment.issuer-url=http://localhost:18099/mock-issuer"
        })
@Transactional
@DisplayName("여러 상품 결제 시 적립금 분배")
class CheckoutPointSplitTest {

    /** V8 시드의 이벤트가 상품을 참조한다. 상품보다 먼저 지운다. */
    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private static final String 고객 = "split01";
    private static final long 시작잔액 = 100_000;

    private Product 비싼것;
    private Product 싼것;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        flashSaleRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        customerRepository.save(new Customer(고객, "$2a$10$h", Money.of(시작잔액)));
        비싼것 = productRepository.save(new Product("레티놀 앰플", Money.of(58_000), 100));
        싼것 = productRepository.save(new Product("마스크팩", Money.of(9_900), 100));
    }

    private void 결제(long usePoint) {
        checkoutService.checkout(고객, new CheckoutRequest(
                List.of(new CheckoutRequest.Item(비싼것.getId(), 1),
                        new CheckoutRequest.Item(싼것.getId(), 1)),
                PaymentMethod.CARD, usePoint,
                new CardPaymentRequest("4242424242424242", "12/29", "123"),
                null, null));
    }

    private long 잔액() {
        return customerRepository.findById(고객).orElseThrow().getPoint().getAmount();
    }

    private long 항목적립금(Product p) {
        return orderItemRepository
                .findByCustomerAndProduct(customerRepository.findById(고객).orElseThrow(), p)
                .map(OrderItem::getUsedPoint)
                .map(Money::getAmount)
                .orElse(0L);
    }

    @Test
    @DisplayName("적립금은 개수가 아니라 금액 비율로 나뉜다")
    void splitsByAmountNotByCount() {
        결제(20_000);

        // 합계 67,900원 중 앰플이 58,000원(85.4%), 마스크팩이 9,900원(14.6%)이다.
        // 개수로 나누면 10,000 / 10,000이 되는데, 그건 마스크팩 가격보다도 크다.
        long 앰플몫 = 항목적립금(비싼것);
        long 팩몫 = 항목적립금(싼것);

        // 금액 비율대로면 앰플 17,083 / 팩 2,917 이다.
        // 개수로 나누면 10,000 / 10,000이 되는데, 그건 마스크팩 가격보다도 크다.
        assertThat(앰플몫)
                .as("앰플이 총액의 85%%를 차지하므로 적립금도 그만큼 부담해야 한다. 앰플 %d / 팩 %d",
                        앰플몫, 팩몫)
                .isEqualTo(20_000L * 58_000 / 67_900);

        assertThat(팩몫).as("나머지를 마지막 항목이 가져가 합계가 맞아야 한다")
                .isEqualTo(20_000 - 앰플몫);

        // 어떤 항목에도 그 상품 가격보다 많은 적립금이 배정될 수 없다.
        assertThat(팩몫).as("마스크팩(9,900원)에 그보다 많은 적립금이 배정됐다").isLessThanOrEqualTo(9_900);
    }

    @Test
    @DisplayName("항목별 적립금의 합이 실제 사용액과 정확히 같다")
    void splitSumsToRequestedAmount() {
        결제(20_000);

        // 나눠 담은 것의 합이 원래 금액과 달라지면, 그 차액은 아무도 낸 적이 없는 돈이 된다.
        assertThat(항목적립금(비싼것) + 항목적립금(싼것))
                .as("배정 합계가 사용액과 다르다")
                .isEqualTo(20_000);
    }

    @Test
    @DisplayName("신청한 적립금이 한 푼도 남김없이 차감된다")
    void chargesExactlyWhatWasRequested() {
        결제(20_000);

        // 적립을 되돌리면 순수 차감액이 나온다. 그 값이 신청액과 같아야 한다.
        //
        // 예전에는 여기서 100원이 비었다. 응답은 "20,000 사용"이라고 하는데
        // 실제로는 19,900만 빠져나갔다. 즉 고객이 100원을 덜 낸 상태로 주문이 성립했다.
        long 적립 = 적립합계();
        assertThat(시작잔액 - 잔액() + 적립)
                .as("신청한 적립금과 실제 차감액이 다르다")
                .isEqualTo(20_000);
    }

    /**
     * 이 주문에서 지급되는 적립액.
     *
     * <p>항목별로 계산해 더한다. 서비스와 같은 방식이어야 한다. 합계에 한 번 적용하면
     * 내림 위치가 달라져 1원이 어긋난다 — 그것이 D46에서 고친 두 번째 문제다.
     */
    private long 적립합계() {
        long 앰플몫 = 20_000L * 58_000 / 67_900;
        long 팩몫 = 20_000 - 앰플몫;
        return (58_000 - 앰플몫) * 5 / 100 + (9_900 - 팩몫) * 5 / 100;
    }

    @Test
    @DisplayName("지급한 적립과 회수한 적립이 정확히 같다")
    void rewardGrantAndClawbackMatch() {
        long 지급전 = 잔액();
        결제(20_000);
        long 지급 = 잔액() - 지급전 + 20_000;   // 적립금 사용분을 되돌린 순증가분

        orderService.cancelOrder(고객, new OrderRequest(비싼것.getId(), 1));
        orderService.cancelOrder(고객, new OrderRequest(싼것.getId(), 1));

        // 지급은 주문 단위, 회수는 항목 단위로 하면 내림이 어긋나 1P가 남는다.
        // 같은 단위로 계산해야 정확히 상쇄된다.
        assertThat(잔액())
                .as("지급 %dP를 회수했는데 잔액이 처음과 다르다", 지급)
                .isEqualTo(시작잔액);
    }

    @Test
    @DisplayName("전부 취소하면 처음 잔액으로 정확히 돌아온다")
    void fullCancelRestoresOriginalBalance() {
        결제(20_000);

        orderService.cancelOrder(고객, new OrderRequest(비싼것.getId(), 1));
        orderService.cancelOrder(고객, new OrderRequest(싼것.getId(), 1));

        // 사고 전부 무르면 아무 일도 없었던 것과 같아야 한다.
        // 어긋나면 주문·취소를 반복해 포인트를 만들거나 잃을 수 있다.
        assertThat(잔액())
                .as("사고 전부 취소했는데 잔액이 처음과 다르다")
                .isEqualTo(시작잔액);
    }

    @Test
    @DisplayName("포인트를 쓰지 않으면 항목 적립금은 모두 0이다")
    void noPointMeansNoSplit() {
        결제(0);

        assertThat(항목적립금(비싼것)).isZero();
        assertThat(항목적립금(싼것)).isZero();
    }
}
