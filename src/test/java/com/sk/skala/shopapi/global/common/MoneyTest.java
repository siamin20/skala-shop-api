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

        @Test
        @DisplayName("곱셈 결과가 long 범위를 넘으면 양수로 되돌아오지 않고 거부한다")
        void rejectMultiplicationOverflow() {
            // 2^62 × 4 = 2^64 이며, 검사 없이 곱하면 0이 되어 생성자의 음수 검사를 통과한다.
            // 즉 예외 없이 "0원"이라는 틀린 총액이 만들어진다.
            Money huge = Money.of(1L << 62);

            assertThatThrownBy(() -> huge.times(4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("너무 큽니다");
        }

        @Test
        @DisplayName("덧셈 결과가 long 범위를 넘으면 거부한다")
        void rejectAdditionOverflow() {
            Money max = Money.of(Long.MAX_VALUE);

            assertThatThrownBy(() -> max.plus(Money.of(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("너무 큽니다");
        }

        @Test
        @DisplayName("범위 안의 큰 값은 정상 계산된다")
        void allowLargeButValidAmounts() {
            assertThat(Money.of(Long.MAX_VALUE).times(1).getAmount()).isEqualTo(Long.MAX_VALUE);
            assertThat(Money.of(Long.MAX_VALUE / 2).plus(Money.of(1)).getAmount())
                    .isEqualTo(Long.MAX_VALUE / 2 + 1);
        }
    }

    @Nested
    @DisplayName("비례 계산")
    class Proportion {

        @Test
        @DisplayName("전체 중 일부에 해당하는 금액을 구한다")
        void proportion() {
            assertThat(Money.of(30_000).proportion(1, 3)).isEqualTo(Money.of(10_000));
            assertThat(Money.of(30_000).proportion(2, 3)).isEqualTo(Money.of(20_000));
        }

        @Test
        @DisplayName("나누어떨어지지 않으면 내림한다")
        void floorsRemainder() {
            // 10,000 × 1 / 3 = 3333.33... → 3,333
            assertThat(Money.of(10_000).proportion(1, 3)).isEqualTo(Money.of(3_333));
        }

        @Test
        @DisplayName("중간 계산이 넘쳐도 결과가 범위 안이면 정상 계산한다")
        void noSpuriousOverflow() {
            // amount × part를 먼저 계산하면 Long.MAX_VALUE × 2 로 넘친다.
            // 하지만 결과 (MAX × 2 / 3)은 범위 안이므로 거부하면 안 된다.
            Money huge = Money.of(Long.MAX_VALUE);

            Money result = huge.proportion(2, 3);

            // 몫·나머지 분리 계산의 정확성까지 확인한다
            assertThat(result.getAmount())
                    .isEqualTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)
                            .multiply(java.math.BigInteger.TWO)
                            .divide(java.math.BigInteger.valueOf(3))
                            .longValueExact());
        }

        @Test
        @DisplayName("전체와 같은 수량이면 금액 전부를 돌려준다")
        void wholeReturnsAll() {
            assertThat(Money.of(30_001).proportion(3, 3)).isEqualTo(Money.of(30_001));
        }

        @Test
        @DisplayName("범위를 벗어난 수량은 거부한다")
        void rejectInvalidRange() {
            assertThatThrownBy(() -> Money.of(1_000).proportion(4, 3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Money.of(1_000).proportion(1, 0))
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
