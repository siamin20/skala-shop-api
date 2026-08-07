package com.sk.skala.shopapi.order.domain;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.product.domain.Product;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객이 주문한 상품 한 건.
 *
 * <p>{@link Customer}와 {@link Product}를 잇는 매핑 엔티티다. 같은 상품을 다시 주문하면
 * 새 행을 만들지 않고 {@link #increase(int)}로 수량만 누적한다. 취소로 수량이 0이 되면
 * {@link #isEmpty()}가 참이 되고, 서비스가 이 항목을 삭제한다.
 *
 * <p>그래서 (고객, 상품) 조합은 항상 한 행뿐이며 유니크 제약으로 강제한다.
 * 애플리케이션에서만 막으면 동시 요청 두 개가 동시에 "없음"을 확인하고 각자 행을 만들 수 있다.
 *
 * <p>{@code unitPrice}는 주문 시점의 단가를 복사해 둔 값이다.
 * 상품 가격이 나중에 바뀌어도 이미 주문한 건의 환불 금액이 흔들리지 않게 하기 위해서다.
 * 이 필드가 없으면 1만 원에 산 상품을 가격 인상 뒤 취소했을 때 2만 원이 환급된다.
 *
 * <p>애그리게이트 경계에 대해서는 로컬 {@code docs/02-domain-design.md}를 참고한다.
 * 이 엔티티는 독립 리포지토리를 갖고, 포인트와 수량의 정합성은 서비스의 트랜잭션이 책임진다.
 */
@Entity
@Table(
        name = "order_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_item_customer_product",
                columnNames = {"customer_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 주문한 고객.
     *
     * <p>{@code LAZY}로 두는 이유는, 주문 목록을 조회할 때 대부분 상품 정보만 필요하고
     * 고객은 이미 손에 있기 때문이다. {@code EAGER}면 목록을 읽을 때마다 고객을 다시 조회한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 주문한 상품. 같은 이유로 {@code LAZY}다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 주문 수량. 항상 1 이상이며, 0이 되면 이 항목 자체가 삭제된다. */
    @Column(nullable = false)
    private int quantity;

    /** 주문 시점의 단가 스냅샷. 상품 가격이 바뀌어도 이 값은 그대로다. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    /**
     * 새 주문 항목을 만든다.
     *
     * <p>단가는 인자로 받지 않고 상품에서 그 시점 값을 읽어 복사한다.
     * 호출하는 쪽이 임의의 단가를 넘길 수 있으면 가격을 조작할 여지가 생긴다.
     *
     * @param customer 주문한 고객
     * @param product  주문한 상품
     * @param quantity 주문 수량. 1 이상이어야 한다
     * @throws IllegalArgumentException 수량이 1 미만인 경우
     */
    public OrderItem(Customer customer, Product product, int quantity) {
        validateQuantity(quantity);
        this.customer = customer;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
    }

    /**
     * 수량을 누적한다. 같은 상품을 다시 주문했을 때 쓴다.
     *
     * <p>단가는 갱신하지 않는다. 처음 주문한 시점의 가격을 유지해야
     * 부분 취소했을 때 환급액을 계산할 기준이 하나로 유지된다.
     *
     * @throws IllegalArgumentException 더할 수량이 1 미만인 경우
     */
    public void increase(int quantity) {
        validateQuantity(quantity);
        try {
            // 단순 덧셈이면 int 범위를 넘을 때 음수로 뒤집혀 "수량은 항상 1 이상"이라는 불변식이 깨진다.
            // addExact는 넘칠 때 조용히 뒤집지 않고 예외를 던진다.
            this.quantity = Math.addExact(this.quantity, quantity);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "주문 수량이 너무 큽니다: %d + %d".formatted(this.quantity, quantity));
        }
    }

    /**
     * 수량을 줄이고 환급할 금액을 반환한다. 주문 취소에 쓴다.
     *
     * <p>검증·차감·환급액 계산을 한 메서드로 묶은 이유가 있다. 이것을 나눠 두면
     * 호출하는 쪽이 순서를 지켜야 하는 함정이 생긴다. 차감을 먼저 하면 남은 수량이 이미 줄어
     * 환급액을 잘못 계산하고, 환급액을 먼저 구하면 검증 전에 금액이 정해진다.
     * 한 번에 처리하면 어떤 순서로 부르든 틀릴 수가 없다.
     *
     * <p>보유한 수량보다 많이 취소할 수 없다. 막지 않으면 수량이 음수가 되고,
     * 산 적 없는 만큼 포인트를 환급받을 수 있게 된다.
     *
     * <p>환급액은 현재 상품 가격이 아니라 주문 시점 단가로 계산한다.
     *
     * @param quantity 취소할 수량. 1 이상이어야 한다
     * @return 돌려줄 금액
     * @throws IllegalArgumentException 취소 수량이 1 미만인 경우
     * @throws BusinessException        보유 수량보다 많이 취소하면 {@link ErrorCode#INSUFFICIENT_QUANTITY}
     */
    public Money cancel(int quantity) {
        validateQuantity(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_QUANTITY,
                    "취소 요청 %d개, 주문 수량 %d개".formatted(quantity, this.quantity));
        }
        Money refund = unitPrice.times(quantity);
        this.quantity -= quantity;
        return refund;
    }

    /**
     * 남은 수량이 없으면 참.
     *
     * <p>수량 0인 행을 남겨두면 주문 목록에 "0개 주문한 상품"이 보이고,
     * (고객, 상품) 유니크 제약 때문에 나중에 같은 상품을 다시 살 수도 없다.
     * 그래서 서비스는 이 값이 참이면 항목을 삭제한다.
     */
    public boolean isEmpty() {
        return quantity == 0;
    }

    /** 남은 수량 기준 총액. 단가 스냅샷을 쓰므로 상품 가격이 바뀌어도 값이 변하지 않는다. */
    public Money totalPrice() {
        return unitPrice.times(quantity);
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
        }
    }
}
