package com.sk.skala.shopapi.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sk.skala.shopapi.global.common.Money;

/**
 * {@link Product} 단위 테스트.
 *
 * <p>과제 명세의 입력값 검증(상품명 필수, 가격 0원 불가)이 생성자와 변경 메서드
 * 양쪽에서 모두 동작하는지 확인한다. 한쪽만 막으면 우회 경로가 남는다.
 */
class ProductTest {

    @Test
    @DisplayName("상품명과 가격으로 생성된다")
    void create() {
        Product product = new Product("무선마우스", Money.of(15_000), 1000);

        assertThat(product.getName()).isEqualTo("무선마우스");
        assertThat(product.getPrice()).isEqualTo(Money.of(15_000));
    }

    @Test
    @DisplayName("상품명이 비어 있으면 생성할 수 없다")
    void rejectBlankName() {
        assertThatThrownBy(() -> new Product("  ", Money.of(15_000), 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품명");
    }

    @Test
    @DisplayName("가격이 0원이면 생성할 수 없다")
    void rejectZeroPrice() {
        assertThatThrownBy(() -> new Product("무선마우스", Money.ZERO, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가격");
    }

    @Test
    @DisplayName("상품명과 가격을 변경할 수 있다")
    void change() {
        Product product = new Product("무선마우스", Money.of(15_000), 1000);

        product.changeName("무선마우스 v2");
        product.changePrice(Money.of(18_000));

        assertThat(product.getName()).isEqualTo("무선마우스 v2");
        assertThat(product.getPrice()).isEqualTo(Money.of(18_000));
    }

    @Test
    @DisplayName("변경할 때도 생성과 같은 규칙이 적용된다")
    void changeAppliesSameValidation() {
        Product product = new Product("무선마우스", Money.of(15_000), 1000);

        assertThatThrownBy(() -> product.changeName(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.changePrice(Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        // 실패했으므로 원래 값이 유지되어야 한다
        assertThat(product.getName()).isEqualTo("무선마우스");
        assertThat(product.getPrice()).isEqualTo(Money.of(15_000));
    }

    @Test
    @DisplayName("상품명 앞뒤 공백을 제거해 저장한다")
    void trimName() {
        // 다듬지 않으면 "무선마우스"와 "무선마우스 "가 유니크 제약을 통과해 중복 등록된다
        Product product = new Product("  무선마우스  ", Money.of(15_000), 1000);
        assertThat(product.getName()).isEqualTo("무선마우스");

        product.changeName("\tUSB허브\n");
        assertThat(product.getName()).isEqualTo("USB허브");
    }

    @Test
    @DisplayName("수량만큼의 총액을 계산한다")
    void totalPriceOf() {
        Product product = new Product("USB허브", Money.of(39_000), 1000);

        assertThat(product.totalPriceOf(3)).isEqualTo(Money.of(117_000));
    }
}
