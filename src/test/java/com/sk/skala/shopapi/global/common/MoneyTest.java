package com.sk.skala.shopapi.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Money} 단위 테스트.
 *
 * <p>값 객체의 두 가지 성질을 고정한다. 음수가 만들어지지 않는다는 것과,
 * 연산이 원본을 바꾸지 않고 새 값을 돌려준다는 것이다.
 */
class MoneyTest {

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("0원 이상이면 만들 수 있다")
        void createWithNonNegativeAmount() {
            assertThat(Money.of(0).getAmount()).isZero();
            assertThat(Money.of(15_000).getAmount()).isEqualTo(15_000);
        }

        @Test
        @DisplayName("음수 금액은 만들 수 없다")
        void rejectNegativeAmount() {
            assertThatThrownBy(() -> Money.of(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("음수");
        }
    }

    @Nested
    @DisplayName("연산")
    class Operations {

        @Test
        @DisplayName("더하기와 빼기는 원본을 바꾸지 않고 새 값을 반환한다")
        void operationsAreImmutable() {
            Money original = Money.of(10_000);

            Money added = original.plus(Money.of(5_000));
            Money subtracted = original.minus(Money.of(3_000));

            assertThat(original.getAmount()).isEqualTo(10_000);
            assertThat(added.getAmount()).isEqualTo(15_000);
            assertThat(subtracted.getAmount()).isEqualTo(7_000);
        }

        @Test
        @DisplayName("결과가 음수가 되는 뺄셈은 막는다")
        void rejectSubtractionBelowZero() {
            assertThatThrownBy(() -> Money.of(1_000).minus(Money.of(1_001)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("수량을 곱해 총액을 구한다")
        void multiplyByQuantity() {
            assertThat(Money.of(15_000).times(3)).isEqualTo(Money.of(45_000));
        }

        @Test
        @DisplayName("0을 곱하면 0원이다")
        void multiplyByZero() {
            assertThat(Money.of(15_000).times(0)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("음수 수량은 곱할 수 없다")
        void rejectNegativeQuantity() {
            assertThatThrownBy(() -> Money.of(15_000).times(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("비교")
    class Comparison {

        @Test
        @DisplayName("금액이 같으면 같은 값으로 본다")
        void equalByAmount() {
            assertThat(Money.of(10_000)).isEqualTo(Money.of(10_000));
            assertThat(Money.of(10_000)).hasSameHashCodeAs(Money.of(10_000));
        }

        @Test
        @DisplayName("잔액 부족을 판단한다")
        void isLessThan() {
            assertThat(Money.of(9_999).isLessThan(Money.of(10_000))).isTrue();
            assertThat(Money.of(10_000).isLessThan(Money.of(10_000))).isFalse();
        }

        @Test
        @DisplayName("부동소수점과 달리 오차 없이 누적된다")
        void noFloatingPointError() {
            // D1: Double이었다면 0.1을 10번 더해도 1.0이 되지 않는다. 정수라 그 문제가 없다.
            Money total = Money.ZERO;
            for (int i = 0; i < 10; i++) {
                total = total.plus(Money.of(1_000));
            }
            assertThat(total).isEqualTo(Money.of(10_000));
        }
    }
}
