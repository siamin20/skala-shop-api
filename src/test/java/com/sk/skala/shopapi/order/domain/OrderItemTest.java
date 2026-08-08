package com.sk.skala.shopapi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.product.domain.Product;

/**
 * {@link OrderItem} 단위 테스트.
 *
 * <p>과제 명세의 핵심 규칙 두 가지를 고정한다.
 * 재주문 시 수량이 누적된다는 것과, 취소로 0이 되면 항목이 비게 된다는 것이다.
 * 여기에 단가 스냅샷이 상품 가격 변동에 영향받지 않는다는 것까지 확인한다.
 */
class OrderItemTest {

    private final Customer 고객 = new Customer("skala01", "$2a$10$hashed", Money.of(1_000_000));
    private final Product 무선마우스 = new Product("무선마우스", Money.of(15_000), 1000);

    @Test
    @DisplayName("주문 시점의 단가를 복사해 생성된다")
    void createWithUnitPriceSnapshot() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(Money.of(15_000));
        assertThat(item.totalPrice()).isEqualTo(Money.of(30_000));
    }

    @Test
    @DisplayName("수량 0 이하로는 주문할 수 없다")
    void rejectNonPositiveQuantity() {
        assertThatThrownBy(() -> new OrderItem(고객, 무선마우스, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 상품을 다시 주문하면 수량이 누적된다")
    void increaseAccumulatesQuantity() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        item.increase(3, Money.of(45_000));

        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.totalPrice()).isEqualTo(Money.of(75_000));
    }

    @Test
    @DisplayName("취소하면 수량이 줄고 환급액을 돌려준다")
    void cancelReducesQuantityAndReturnsRefund() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        Money refund = item.cancel(1);

        assertThat(refund).isEqualTo(Money.of(15_000));
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("전량 취소하면 빈 항목이 된다")
    void becomeEmptyWhenFullyCancelled() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        Money refund = item.cancel(2);

        assertThat(refund).isEqualTo(Money.of(30_000));
        assertThat(item.getQuantity()).isZero();
        assertThat(item.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("보유 수량보다 많이 취소하면 실패하고 수량은 그대로다")
    void rejectCancellationOverQuantity() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        assertThatThrownBy(() -> item.cancel(3))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);

        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("수량이 int 범위를 넘도록 누적하면 음수로 뒤집히지 않고 거부한다")
    void rejectQuantityOverflow() {
        OrderItem item = new OrderItem(고객, 무선마우스, Integer.MAX_VALUE);

        assertThatThrownBy(() -> item.increase(1, Money.of(15_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("너무 큽니다");

        // 실패한 뒤에도 수량이 음수로 뒤집히지 않아야 한다
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("주문 후 상품 가격이 올라도 환급액은 실제 결제액을 따른다")
    void refundUsesPaidAmountNotCurrentPrice() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        // 주문 뒤 상품 가격이 두 배로 오른 상황
        무선마우스.changePrice(Money.of(30_000));

        assertThat(item.totalPrice()).isEqualTo(Money.of(30_000));
        // 환급도 인상된 30,000원이 아니라 결제한 15,000원을 따른다
        assertThat(item.cancel(1)).isEqualTo(Money.of(15_000));
    }

    @Test
    @DisplayName("가격이 오른 뒤 재주문해도 결제액과 환급액이 일치한다")
    void refundMatchesPaidAmountAfterPriceChange() {
        // 15,000원 2개 = 30,000원 결제
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        // 가격 인상 후 30,000원짜리 1개를 추가 결제 → 누적 60,000원
        무선마우스.changePrice(Money.of(30_000));
        item.increase(1, Money.of(30_000));

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.totalPrice()).isEqualTo(Money.of(60_000));

        // 단가 스냅샷 방식이었다면 15,000 × 3 = 45,000만 돌아가 고객이 15,000원을 잃는다.
        assertThat(item.cancel(3)).isEqualTo(Money.of(60_000));
    }

    @Test
    @DisplayName("나눠서 취소해도 환급 합계가 결제액과 정확히 일치한다")
    void partialCancellationsSumToPaidAmount() {
        // 나누어떨어지지 않는 금액으로 잔돈 처리를 확인한다.
        // 10,000원 3개 = 30,000원. 1개씩 세 번 취소.
        Product 상품 = new Product("잔돈확인용", Money.of(10_000), 1000);
        OrderItem item = new OrderItem(고객, 상품, 3);
        상품.changePrice(Money.of(1L));
        item.increase(1, Money.of(1L));   // 누적 30,001원 / 4개

        Money 합계 = item.cancel(1).plus(item.cancel(1)).plus(item.cancel(1)).plus(item.cancel(1));

        // 비례 계산에서 내림된 잔돈이 마지막 전량 취소에서 정산된다
        assertThat(합계).isEqualTo(Money.of(30_001));
        assertThat(item.isEmpty()).isTrue();
    }
}
