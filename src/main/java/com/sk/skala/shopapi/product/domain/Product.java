package com.sk.skala.shopapi.product.domain;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

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
     * 남은 재고 수량. (D22)
     *
     * <p>명세에는 없는 필드다. 재고가 없으면 <b>여러 사용자가 같은 행을 다투는 상황</b>
     * 자체가 만들어지지 않는다. 포인트는 본인만 바꾸므로 남과 경합할 일이 없기 때문이다.
     * 락 전략을 비교하려면 경합하는 자원이 하나는 필요하다. (D8)
     *
     * <p>이 필드는 <b>비관적 락</b>으로 보호한다. 인기 상품 한 행에 요청이 몰리는
     * hot row가 되기 때문이다. 낙관적 락을 쓰면 충돌이 잦아 재시도가 대부분 또 실패한다.
     * 판단 근거는 {@code docs/04-concurrency.md}에 있다.
     */
    @Column(name = "product_stock", nullable = false)
    private int stock;

    /**
     * 대분류. 화면 상단 탭에 해당한다. (D35)
     *
     * <p>이름으로 추정하지 않고 컬럼으로 둔 이유는 <b>서버에서 걸러야 페이지 처리와
     * 맞기 때문이다.</b> 화면에서 거르면 서버가 10개를 잘라 보낸 뒤 그중 3개만 남아
     * "10개 보기"인데 3개만 있는 페이지가 나온다.
     */
    @Column(nullable = false, length = 30)
    private String category;

    /** 소분류. 대분류만 두면 스킨케어에 절반이 몰려 실제로 좁혀지지 않는다. */
    @Column(nullable = false, length = 30)
    private String subcategory;

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
    /**
     * 상품을 만든다.
     *
     * <p>재고를 생략할 수 있는 생성자를 두지 않았다. 두면 재고를 깜빡한 상품이
     * <b>등록하자마자 품절 상태</b>로 조용히 생긴다. 필요한 값을 명시하게 강제하는 편이
     * 호출부를 조금 번거롭게 하더라도 안전하다.
     */
    public Product(String name, Money price, int stock) {
        // 분류를 지정하지 않으면 기타로 둔다. 테스트가 만드는 상품처럼
        // 분류가 의미 없는 경우에 매번 값을 정하게 하지 않는다.
        this(name, price, stock, "기타", "기타");
    }

    public Product(String name, Money price, int stock, String category, String subcategory) {
        validatePrice(price);
        validateStock(stock);
        this.name = normalizeName(name);
        this.price = price;
        this.stock = stock;
        this.category = (category == null || category.isBlank()) ? "기타" : category;
        this.subcategory = (subcategory == null || subcategory.isBlank()) ? "기타" : subcategory;
    }

    /**
     * 상품명을 바꾼다.
     *
     * @throws IllegalArgumentException 상품명이 비어 있는 경우
     */
    public void changeName(String name) {
        this.name = normalizeName(name);
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

    /**
     * 재고를 차감한다.
     *
     * <p>재고 검사를 이 메서드 안에서 하는 이유는 D2의 원칙과 같다. 검사를 호출하는 쪽에
     * 맡기면 검사를 빠뜨린 경로가 하나만 생겨도 재고가 음수가 된다.
     *
     * <p><b>이 메서드만으로는 동시성 안전이 보장되지 않는다.</b> 두 트랜잭션이 같은 값을
     * 읽으면 둘 다 검사를 통과한다. 호출하기 전에 비관적 락으로 행을 잡아야 하며,
     * 그 책임은 {@code OrderService}에 있다. DB의 CHECK 제약이 마지막 방어선이다.
     *
     * @throws BusinessException 재고가 부족하면 {@code OUT_OF_STOCK}
     */
    public void deductStock(int quantity) {
        if (quantity > this.stock) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK,
                    "%s의 재고가 부족합니다. 남은 수량: %d".formatted(name, stock));
        }
        this.stock -= quantity;
    }

    /**
     * 주문 취소로 재고를 되돌린다.
     *
     * <p>차감과 달리 상한을 검사하지 않는다. 되돌리는 수량이 맞는지는 주문 항목이
     * 알고 있고, 여기서 다시 검사하려면 원래 재고를 알아야 하는데 그 정보가 없다.
     */
    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

    /**
     * 상품명을 검증하고 앞뒤 공백을 제거한다.
     *
     * <p>{@code public static}인 이유는 <b>중복 검사도 같은 규칙으로 비교해야</b> 하기 때문이다.
     * 서비스가 {@code name.trim()}을 직접 부르면 정규화 규칙이 두 곳에 흩어진다.
     * 나중에 여기서 규칙이 하나라도 바뀌면(예: 연속 공백 축약) 검사와 저장이 어긋나
     * 중복이 조용히 통과한다.
     *
     * <p>공백을 다듬지 않으면 {@code "무선마우스"}와 {@code "무선마우스 "}가 서로 다른 값이 되어
     * {@code product_name}의 유니크 제약을 그대로 통과한다. 목록에는 같아 보이는 상품이 두 줄로 뜨고,
     * 중복 검사도 뚫린다. 저장 직전 한 곳에서 다듬어 생성과 변경 양쪽에 같은 규칙을 적용한다.
     */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다");
        }
        return name.trim();
    }

    private void validateStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("재고는 음수일 수 없습니다");
        }
    }

    private void validatePrice(Money price) {
        if (price == null || price.isZero()) {
            throw new IllegalArgumentException("상품 가격은 0원일 수 없습니다");
        }
    }
}
