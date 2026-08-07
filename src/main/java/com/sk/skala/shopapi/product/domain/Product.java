package com.sk.skala.shopapi.product.domain;

import com.sk.skala.shopapi.global.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 상품.
 *
 * <p>D2: {@code @Setter}를 두지 않는다. 이름과 가격은 {@link #changeName(String)},
 * {@link #changePrice(Money)}로만 바꿀 수 있다. Setter를 열어두면 검증을 건너뛰고
 * 값을 넣는 경로가 코드 어디에나 생기기 때문이다.
 *
 * <p>필드 이름은 {@code name}, {@code price}지만 컬럼은 과제 명세의
 * {@code product_name}, {@code product_price}에 맞춘다. 클래스 안에서
 * {@code product.getProductName()}처럼 접두사가 반복되는 어색함을 피하면서
 * 테이블 구조는 명세 그대로 유지하기 위해서다.
 *
 * <p>재고({@code stock})는 P4에서 추가한다. P1은 과제 명세 범위만 구현한다.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    /**
     * 자동 증가 식별자.
     *
     * <p>{@code IDENTITY}는 DB의 auto increment에 맡기는 방식이다.
     * H2와 PostgreSQL 모두 지원하며 시퀀스를 따로 만들지 않아도 된다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상품명. 중복을 허용하지 않으므로 DB에도 유니크 제약을 건다. */
    @Column(name = "product_name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 판매 가격.
     *
     * <p>{@link Money}는 {@code amount} 필드 하나를 가진 값 객체이고,
     * 여기서 그 컬럼 이름을 {@code product_price}로 지정한다.
     * {@code @AttributeOverride}가 없으면 컬럼이 그냥 {@code amount}가 된다.
     */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "product_price", nullable = false))
    private Money price;

    /**
     * 새 상품을 만든다.
     *
     * <p>필수값 검사를 생성자에서 하는 이유는, 이 검사를 통과하지 못한 {@code Product}가
     * 애초에 만들어지지 않게 하기 위해서다. 서비스에서 검사하면 새 호출 경로가 생길 때마다
     * 검사를 다시 넣어야 하고, 한 군데만 빠뜨려도 잘못된 데이터가 저장된다.
     *
     * @param name  상품명. 비어 있을 수 없다
     * @param price 판매 가격. 0원일 수 없다
     * @throws IllegalArgumentException 상품명이 비었거나 가격이 0원인 경우
     */
    public Product(String name, Money price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.price = price;
    }

    /**
     * 상품명을 바꾼다.
     *
     * @throws IllegalArgumentException 상품명이 비어 있는 경우
     */
    public void changeName(String name) {
        validateName(name);
        this.name = name;
    }

    /**
     * 판매 가격을 바꾼다.
     *
     * <p>이미 주문된 건의 금액은 영향을 받지 않는다.
     * {@code OrderItem}이 주문 시점 단가를 따로 복사해 두기 때문이다.
     *
     * @throws IllegalArgumentException 가격이 0원인 경우
     */
    public void changePrice(Money price) {
        validatePrice(price);
        this.price = price;
    }

    /** 수량만큼의 총액을 구한다. 주문 금액 계산에 쓴다. */
    public Money totalPriceOf(int quantity) {
        return price.times(quantity);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다");
        }
    }

    private void validatePrice(Money price) {
        if (price == null || price.isZero()) {
            throw new IllegalArgumentException("상품 가격은 0원일 수 없습니다");
        }
    }
}
