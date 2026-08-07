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
    private final Product 무선마우스 = new Product("무선마우스", Money.of(15_000));

    @Test
    @DisplayName("주문 시점의 단가를 복사해 생성된다")
    void createWithUnitPriceSnapshot() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(Money.of(15_000));
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

        item.increase(3);

        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.totalPrice()).isEqualTo(Money.of(75_000));
    }

    @Test
    @DisplayName("취소하면 수량이 줄어든다")
    void decreaseReducesQuantity() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        item.decrease(1);

        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("전량 취소하면 빈 항목이 된다")
    void becomeEmptyWhenFullyCancelled() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        item.decrease(2);

        assertThat(item.getQuantity()).isZero();
        assertThat(item.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("보유 수량보다 많이 취소하면 실패하고 수량은 그대로다")
    void rejectCancellationOverQuantity() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        assertThatThrownBy(() -> item.decrease(3))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);

        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 후 상품 가격이 올라도 환급액은 주문 시점 단가를 따른다")
    void refundUsesSnapshotNotCurrentPrice() {
        OrderItem item = new OrderItem(고객, 무선마우스, 2);

        // 주문 뒤 상품 가격이 두 배로 오른 상황
        무선마우스.changePrice(Money.of(30_000));

        assertThat(item.getUnitPrice()).isEqualTo(Money.of(15_000));
        assertThat(item.refundAmountOf(1)).isEqualTo(Money.of(15_000));
        assertThat(item.totalPrice()).isEqualTo(Money.of(30_000));
    }

    @Test
    @DisplayName("재주문해도 단가 스냅샷은 처음 값을 유지한다")
    void increaseKeepsOriginalUnitPrice() {
        OrderItem item = new OrderItem(고객, 무선마우스, 1);

        무선마우스.changePrice(Money.of(30_000));
        item.increase(1);

        assertThat(item.getUnitPrice()).isEqualTo(Money.of(15_000));
        assertThat(item.totalPrice()).isEqualTo(Money.of(30_000));
    }
}
