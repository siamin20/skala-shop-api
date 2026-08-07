package com.sk.skala.shopapi.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 금액을 나타내는 값 객체.
 *
 * <p>D1: 과제 명세는 가격과 포인트를 {@code Double}로 선언하지만 이 프로젝트는 쓰지 않는다.
 * 부동소수점은 십진 소수를 정확히 표현하지 못해 더하고 빼는 사이에 오차가 쌓인다.
 * 원화는 소수점이 없으므로 내부를 {@code long}으로 두는 편이 정확하고 빠르다.
 *
 * <p>값 객체이므로 식별자가 없고, 금액이 같으면 같은 것으로 취급한다.
 * 한 번 만들면 값이 바뀌지 않으며, 연산은 항상 새 인스턴스를 반환한다.
 * 그래서 여러 스레드가 같은 {@code Money}를 공유해도 안전하다.
 *
 * <p>"음수 금액은 존재할 수 없다"는 규칙을 생성자에서 한 번만 막으면
 * 이 타입을 쓰는 모든 코드가 자동으로 그 규칙을 지키게 된다.
 *
 * @see com.sk.skala.shopapi.customer.domain.Customer#deductPoint(Money)
 */
@Embeddable
public class Money implements Comparable<Money> {

    /** 0원. 초기값이 필요할 때 새 인스턴스를 만들지 않고 재사용한다. */
    public static final Money ZERO = new Money(0L);

    /**
     * 원 단위 금액.
     *
     * <p>컬럼 이름은 이 필드가 아니라 이 값을 품는 엔티티에서 정한다.
     * {@code Product}는 {@code product_price}, {@code Customer}는 {@code customer_point}처럼
     * 각자 {@code @AttributeOverride}로 지정한다.
     */
    @Column(nullable = false)
    private long amount;

    /**
     * JPA 전용 기본 생성자.
     *
     * <p>Hibernate가 리플렉션으로 객체를 만들 때 필요하다. {@code protected}로 둬서
     * 애플리케이션 코드가 실수로 0원짜리 빈 객체를 만들지 못하게 막는다.
     */
    protected Money() {
    }

    private Money(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다: " + amount);
        }
        this.amount = amount;
    }

    /**
     * 금액을 만든다.
     *
     * @param amount 원 단위 금액. 0 이상이어야 한다
     * @throws IllegalArgumentException 음수인 경우
     */
    public static Money of(long amount) {
        return new Money(amount);
    }

    public long getAmount() {
        return amount;
    }

    /**
     * 두 금액을 더한 새 값을 반환한다.
     *
     * @throws IllegalArgumentException 결과가 {@code long} 범위를 넘는 경우
     */
    public Money plus(Money other) {
        return new Money(addExact(this.amount, other.amount));
    }

    /**
     * 금액을 뺀 새 값을 반환한다.
     *
     * <p>결과가 음수면 생성자가 막는다. 포인트 차감처럼 "잔액이 모자라면 안 되는" 계산에서
     * 검사를 빠뜨려도 잘못된 값이 저장되지 않는 최후의 방어선이 된다.
     *
     * @throws IllegalArgumentException 결과가 음수인 경우
     */
    public Money minus(Money other) {
        return new Money(this.amount - other.amount);
    }

    /**
     * 수량을 곱한 새 값을 반환한다. 주문 총액(단가 × 수량)을 구할 때 쓴다.
     *
     * <p>단순 곱셈을 쓰면 안 된다. {@code long} 범위를 넘길 때 결과가 <b>양수로</b> 되돌아올 수 있어
     * 생성자의 음수 검사를 그대로 통과한다. 그러면 아무 예외 없이 잘못된 총액이나
     * 환급액이 계산되어 저장된다. 덧셈과 달리 곱셈은 이런 방식으로 조용히 틀릴 수 있다.
     *
     * @param quantity 곱할 수량. 0 이상이어야 한다
     * @throws IllegalArgumentException 수량이 음수이거나 결과가 {@code long} 범위를 넘는 경우
     */
    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("수량은 음수일 수 없습니다: " + quantity);
        }
        try {
            return new Money(Math.multiplyExact(this.amount, quantity));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "금액 계산 결과가 너무 큽니다: %d × %d".formatted(this.amount, quantity));
        }
    }

    /**
     * 넘침을 감지하는 덧셈.
     *
     * <p>{@code ArithmeticException}을 그대로 흘리면 전역 예외 처리기에서 500이 된다.
     * 잘못된 입력이 원인이므로 400으로 나가도록 {@code IllegalArgumentException}으로 옮긴다.
     */
    private static long addExact(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("금액 계산 결과가 너무 큽니다: %d + %d".formatted(a, b));
        }
    }

    /** 이 금액이 {@code other}보다 적으면 true. 잔액 부족 판단에 쓴다. */
    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    public boolean isZero() {
        return this.amount == 0L;
    }

    /**
     * 값이 같으면 같은 객체로 본다.
     *
     * <p>값 객체의 핵심 성질이다. 식별자로 비교하는 엔티티와 여기서 갈린다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return this.amount == other.amount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(amount);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.amount, other.amount);
    }

    @Override
    public String toString() {
        return amount + "원";
    }
}
