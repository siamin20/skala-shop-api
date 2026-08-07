package com.sk.skala.shopapi.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

/**
 * {@link Customer} 단위 테스트.
 *
 * <p>포인트 규칙이 도메인 안에서 지켜지는지 확인한다.
 * 서비스나 컨트롤러를 거치지 않고 도메인만 검증하므로 스프링 컨텍스트가 필요 없고 빠르다.
 */
class CustomerTest {

    private Customer 고객(long 초기포인트) {
        return new Customer("skala01", "$2a$10$hashed", Money.of(초기포인트));
    }

    @Test
    @DisplayName("초기 포인트를 지급받아 생성된다")
    void createWithInitialPoint() {
        Customer customer = 고객(1_000_000);

        assertThat(customer.getCustomerId()).isEqualTo("skala01");
        assertThat(customer.getPoint()).isEqualTo(Money.of(1_000_000));
    }

    @Test
    @DisplayName("아이디가 비어 있으면 생성할 수 없다")
    void rejectBlankCustomerId() {
        assertThatThrownBy(() -> new Customer(" ", "$2a$10$hashed", Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보유 포인트만큼 차감할 수 있다")
    void deductPoint() {
        Customer customer = 고객(1_000_000);

        customer.deductPoint(Money.of(30_000));

        assertThat(customer.getPoint()).isEqualTo(Money.of(970_000));
    }

    @Test
    @DisplayName("잔액과 정확히 같은 금액은 차감된다")
    void deductExactBalance() {
        Customer customer = 고객(30_000);

        customer.deductPoint(Money.of(30_000));

        assertThat(customer.getPoint()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("보유 포인트보다 많이 차감하면 실패하고 잔액은 그대로다")
    void rejectDeductionOverBalance() {
        Customer customer = 고객(12_000);

        assertThatThrownBy(() -> customer.deductPoint(Money.of(30_000)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // 예외가 난 뒤에도 잔액이 깎이지 않아야 한다
        assertThat(customer.getPoint()).isEqualTo(Money.of(12_000));
    }

    @Test
    @DisplayName("취소하면 포인트가 환급된다")
    void refundPoint() {
        Customer customer = 고객(970_000);

        customer.refundPoint(Money.of(15_000));

        assertThat(customer.getPoint()).isEqualTo(Money.of(985_000));
    }

    @Test
    @DisplayName("차감과 환급을 반복해도 잔액이 정확하다")
    void deductAndRefundKeepsBalanceExact() {
        Customer customer = 고객(1_000_000);

        customer.deductPoint(Money.of(15_000));
        customer.deductPoint(Money.of(29_000));
        customer.refundPoint(Money.of(15_000));

        assertThat(customer.getPoint()).isEqualTo(Money.of(971_000));
    }

    @Test
    @DisplayName("본인 여부를 판별한다")
    void isOwner() {
        Customer customer = 고객(0);

        assertThat(customer.isOwner("skala01")).isTrue();
        assertThat(customer.isOwner("skala02")).isFalse();
    }
}
