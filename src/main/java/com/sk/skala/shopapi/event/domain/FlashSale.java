package com.sk.skala.shopapi.event.domain;

import java.time.Instant;

import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.product.domain.Product;

import com.sk.skala.shopapi.global.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 한정 판매 이벤트. (D23)
 *
 * <p>이 프로젝트에서 <b>가장 극단적인 경합 지점</b>이다. P4-A의 상품 재고는 상품마다 행이
 * 나뉘어 경합이 흩어지지만, 여기서는 수천 명이 정확히 같은 하나의 행을 노린다.
 * 락 전략의 차이가 가장 뚜렷하게 드러나는 조건이라 네 방식을 비교하는 무대로 삼았다.
 *
 * <h2>상품 재고와 왜 분리하는가</h2>
 *
 * <p>이벤트 수량은 상품 재고의 일부다. 한 컬럼으로 합치면 "이벤트가 끝나도 일반 판매는
 * 계속된다"를 표현할 수 없고, 이벤트 수량이 소진됐는지 상품이 품절인지 구분할 수 없다.
 *
 * @see com.sk.skala.shopapi.event.app.FlashSaleClaimStrategy
 */
@Entity
@Table(name = "flash_sale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_name", nullable = false, length = 100)
    private String name;

    /**
     * 판매할 상품.
     *
     * <p>{@code LAZY}로 둔다. 선착순 처리 경로에서 상품 정보가 항상 필요한 것은 아니고,
     * 경합이 심한 구간에서 불필요한 조인을 하나라도 줄이는 편이 낫다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * 특가 판매가. (D42)
     *
     * <p>상품 정가와 별개로 이 이벤트에서만 적용된다. "특가"라고 부르면서 정가 그대로
     * 팔면 사용자 입장에서는 <b>특가가 아니라 그냥 재고 제한</b>이다.
     *
     * <p>남은 수량은 급박함을 만들고, 할인율은 이득을 만든다. 둘 다 있어야 참여 이유가 된다.
     */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "sale_price", nullable = false))
    private Money salePrice;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    /** 남은 수량. 경합의 대상이 되는 바로 그 값이다. */
    @Column(name = "remaining", nullable = false)
    private int remaining;

    /**
     * 낙관적 락 버전.
     *
     * <p>네 전략 중 낙관적 락 방식만 이 값에 의존한다. 다른 전략을 쓸 때도 컬럼은 남아
     * Hibernate가 값을 올리지만, 그 전략들은 충돌 판단에 쓰지 않는다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    public FlashSale(String name, Product product, int totalQuantity,
            Instant startsAt, Instant endsAt) {
        // 할인가를 지정하지 않으면 정가 그대로다. 테스트가 만드는 이벤트처럼
        // 할인율이 관심사가 아닌 경우에 매번 값을 정하게 하지 않는다.
        this(name, product, product.getPrice(), totalQuantity, startsAt, endsAt);
    }

    public FlashSale(String name, Product product, Money salePrice, int totalQuantity,
            Instant startsAt, Instant endsAt) {

        if (salePrice == null || salePrice.isZero()) {
            throw new IllegalArgumentException("특가 판매가는 0원일 수 없습니다");
        }
        if (product.getPrice().isLessThan(salePrice)) {
            // 정가보다 비싼 "특가"는 말이 되지 않는다. 입력 실수를 여기서 막는다.
            throw new IllegalArgumentException("특가는 정가보다 비쌀 수 없습니다");
        }
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("이벤트 수량은 1개 이상이어야 합니다");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 뒤여야 합니다");
        }

        this.name = name;
        this.product = product;
        this.salePrice = salePrice;
        this.totalQuantity = totalQuantity;
        this.remaining = totalQuantity;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /**
     * 수량을 차감한다.
     *
     * <p><b>이 메서드만으로는 동시성 안전이 보장되지 않는다.</b> 두 트랜잭션이 같은 값을
     * 읽으면 둘 다 검사를 통과한다. 어떻게 보호할지는 전략이 정한다.
     * 낙관적 락은 버전 충돌로, 비관적 락은 행 잠금으로 막는다.
     *
     * @throws BusinessException 남은 수량이 부족하면 {@code SOLD_OUT}
     */
    public void claim(int quantity) {
        if (quantity > this.remaining) {
            throw new BusinessException(ErrorCode.SOLD_OUT,
                    "%s의 남은 수량이 부족합니다. 남은 수량: %d".formatted(name, remaining));
        }
        this.remaining -= quantity;
    }

    /** 취소로 수량을 되돌린다. 처음 수량을 넘길 수 없다. */
    public void release(int quantity) {
        this.remaining = Math.min(this.remaining + quantity, this.totalQuantity);
    }

    /**
     * 지금 참여할 수 있는 기간인지 확인한다.
     *
     * <p>기간 검사를 수량 차감보다 <b>먼저</b> 해야 한다. 뒤에 하면 끝난 이벤트에서도
     * 수량이 줄었다가 롤백되는데, 그 사이 다른 요청이 품절을 보게 된다.
     *
     * @throws BusinessException 기간 밖이면 {@code SOLD_OUT}
     */
    public void validateOpen(Instant now) {
        if (now.isBefore(startsAt)) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "아직 시작되지 않은 이벤트입니다");
        }
        if (!now.isBefore(endsAt)) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "종료된 이벤트입니다");
        }
    }

    public int soldQuantity() {
        return totalQuantity - remaining;
    }

    /**
     * 할인율(%). 내림한다.
     *
     * <p>올림하면 실제보다 커 보인다. 39.6%를 40%로 표시하는 것은 사소해 보이지만
     * <b>가격 표시는 올려 말하면 안 되는 종류</b>다.
     */
    public int discountRate() {
        long list = product.getPrice().getAmount();
        if (list <= 0) return 0;
        return (int) ((list - salePrice.getAmount()) * 100 / list);
    }

    /** 정가 대비 아끼는 금액. */
    public Money savedAmount() {
        return product.getPrice().minus(salePrice);
    }

    /** 이 이벤트에서 결제할 총액. */
    public Money totalPriceOf(int quantity) {
        return salePrice.times(quantity);
    }
}
