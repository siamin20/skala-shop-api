package com.sk.skala.shopapi.order.ledger;

import com.sk.skala.shopapi.global.common.Money;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목. 주문 시점의 상품 정보를 <b>복사해서</b> 갖는다. (D43)
 *
 * <p>상품을 외래 키로만 걸면 상품명이 바뀌거나 삭제됐을 때
 * <b>과거 영수증이 함께 바뀐다.</b> 3만원에 산 것이 상품 가격 인상 후 5만원에 산 것으로 보인다.
 *
 * <p>{@code productId}는 남겨둔다. 상품 상세로 이동하거나 재구매할 때 쓴다.
 * 다만 <b>표시에는 쓰지 않는다.</b> 표시는 복사해둔 값으로 한다.
 */
@Entity
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 상품 참조. 재구매·상세 이동에만 쓴다. 표시는 아래 복사본으로 한다. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "line_amount", nullable = false))
    private Money lineAmount;

    /** 이 항목에서 적립금으로 낸 금액. 취소 시 얼마를 적립금으로 돌려줄지 계산한다. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "used_point", nullable = false))
    private Money usedPoint;

    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity;

    public OrderLine(Product product, Money unitPrice, int quantity,
            Money lineAmount, Money usedPoint) {

        this.productId = product.getId();
        this.productName = product.getName();
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = lineAmount;
        this.usedPoint = usedPoint;
        this.cancelledQuantity = 0;
    }

    void assignTo(Order order) {
        this.order = order;
    }

    /** 취소 수량을 누적한다. 주문 수량을 넘길 수 없다. */
    void cancel(int quantity) {
        this.cancelledQuantity = Math.min(this.cancelledQuantity + quantity, this.quantity);
    }

    public boolean isFullyCancelled() {
        return cancelledQuantity >= quantity;
    }

    /** 아직 살아 있는 수량. */
    public int activeQuantity() {
        return quantity - cancelledQuantity;
    }
}
