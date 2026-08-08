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
 * <p>D15: 단가가 아니라 <b>실제로 결제한 누적 총액</b>을 저장한다. 단가 스냅샷을 두면
 * 가격이 바뀐 뒤 재주문할 때 차감과 환급이 어긋난다. 15,000원짜리 2개를 산 뒤 가격이
 * 30,000원으로 오르고 1개를 더 사면 총 60,000원이 빠지는데, 스냅샷은 여전히 15,000원이라
 * 전량 취소 시 45,000원만 돌아온다. <b>고객이 15,000원을 잃는다.</b>
 *
 * <p>총액을 누적하면 차감한 금액이 그대로 환급 재원이 되므로 이 어긋남이 생기지 않는다.
 * 부분 취소는 수량에 비례해 내림 계산하고, 남은 잔돈은 마지막 전량 취소에서 정산된다.
 * 표시용 단가는 {@link #unitPrice()}로 총액에서 파생한다.
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

    /** 실제로 결제한 누적 총액. 주문할 때마다 그 시점 결제액이 더해지고, 취소하면 환급액만큼 빠진다. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    /**
     * 결제 총액 중 포인트로 낸 금액. (D31)
     *
     * <p>나머지는 카드로 낸 것이다. 취소할 때 <b>포인트로 얼마를 돌려주고 카드로 얼마를
     * 환불할지</b> 나누려면 이 값이 필요하다. 없으면 전액을 포인트로 돌려주게 되어
     * 카드로 낸 돈만큼 포인트가 늘어난다.
     *
     * <p>명세의 기본 동작(포인트 전액 결제)에서는 {@code totalAmount}와 같다.
     */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "used_point", nullable = false))
    private Money usedPoint;

    /**
     * 새 주문 항목을 만든다.
     *
     * <p>결제 총액은 인자로 받지 않고 상품에서 그 시점 가격을 읽어 계산한다.
     * 호출하는 쪽이 임의의 금액을 넘길 수 있으면 가격을 조작할 여지가 생긴다.
     *
     * @param customer 주문한 고객
     * @param product  주문한 상품
     * @param quantity 주문 수량. 1 이상이어야 한다
     * @throws IllegalArgumentException 수량이 1 미만인 경우
     */
    public OrderItem(Customer customer, Product product, int quantity) {
        // 명세의 기본 동작. 전액을 포인트로 낸다.
        this(customer, product, quantity, product.totalPriceOf(quantity),
                product.totalPriceOf(quantity));
    }

    /**
     * 결제 내역을 지정해 항목을 만든다. (D31)
     *
     * @param paidAmount 결제 총액
     * @param usedPoint  그중 포인트로 낸 금액
     */
    public OrderItem(Customer customer, Product product, int quantity,
            Money paidAmount, Money usedPoint) {

        validateQuantity(quantity);
        validatePayment(paidAmount, usedPoint);
        this.customer = customer;
        this.product = product;
        this.quantity = quantity;
        this.totalAmount = paidAmount;
        this.usedPoint = usedPoint;
    }

    /**
     * 수량과 결제 총액을 누적한다. 같은 상품을 다시 주문했을 때 쓴다.
     *
     * <p>이번에 실제로 차감한 금액을 함께 받는다. 여기서 상품의 현재 가격을 다시 읽으면
     * 서비스가 차감한 금액과 달라질 수 있고, 그 차이가 그대로 환급 오차가 된다.
     * <b>차감한 쪽이 그 금액을 넘겨주는 것</b>이 두 값을 일치시키는 유일한 방법이다.
     *
     * @param quantity    추가 수량. 1 이상
     * @param paidAmount  이번 주문에서 실제로 차감한 금액
     * @throws IllegalArgumentException 수량이 1 미만이거나 누적 결과가 범위를 넘는 경우
     */
    public void increase(int quantity, Money paidAmount) {
        increase(quantity, paidAmount, paidAmount);
    }

    /** 포인트 사용액을 함께 누적한다. (D31) */
    public void increase(int quantity, Money paidAmount, Money usedPoint) {
        validateQuantity(quantity);
        validatePayment(paidAmount, usedPoint);
        try {
            // 단순 덧셈이면 int 범위를 넘을 때 음수로 뒤집혀 "수량은 항상 1 이상"이라는 불변식이 깨진다.
            // addExact는 넘칠 때 조용히 뒤집지 않고 예외를 던진다.
            this.quantity = Math.addExact(this.quantity, quantity);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "주문 수량이 너무 큽니다: %d + %d".formatted(this.quantity, quantity));
        }
        this.totalAmount = this.totalAmount.plus(paidAmount);
        this.usedPoint = this.usedPoint.plus(usedPoint);
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
     * <p>D15: 환급 재원은 <b>실제로 결제한 누적 총액</b>이다. 상품의 현재 가격과 무관하므로
     * 가격이 오르내려도 결제한 만큼만 정확히 돌아간다.
     *
     * <p>전량 취소는 남은 총액을 <b>그대로</b> 돌려준다. 비례 계산으로 내림하면서 생긴 잔돈이
     * 여기서 한 번에 정산되어, 여러 번 나눠 취소해도 환급 합계가 결제액과 일치한다.
     *
     * @param quantity 취소할 수량. 1 이상이어야 한다
     * @return 돌려줄 금액
     * @throws IllegalArgumentException 취소 수량이 1 미만인 경우
     * @throws BusinessException        보유 수량보다 많이 취소하면 {@link ErrorCode#INSUFFICIENT_QUANTITY}
     */
    public Refund cancel(int quantity) {
        validateQuantity(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_QUANTITY,
                    "취소 요청 %d개, 주문 수량 %d개".formatted(quantity, this.quantity));
        }

        boolean all = (quantity == this.quantity);

        Money refund = all ? this.totalAmount : this.totalAmount.proportion(quantity, this.quantity);
        Money pointBack = all ? this.usedPoint : this.usedPoint.proportion(quantity, this.quantity);

        this.totalAmount = this.totalAmount.minus(refund);
        this.usedPoint = this.usedPoint.minus(pointBack);
        this.quantity -= quantity;

        return new Refund(refund, pointBack);
    }

    /**
     * 환급 내역. (D31)
     *
     * @param total        총 환급액
     * @param pointPortion 그중 포인트로 돌려줄 금액. 나머지는 카드로 환불된다
     */
    public record Refund(Money total, Money pointPortion) {

        /** 카드로 환불할 금액. */
        public Money cardPortion() {
            return total.minus(pointPortion);
        }
    }

    private static void validatePayment(Money paidAmount, Money usedPoint) {
        if (usedPoint == null || paidAmount == null) {
            throw new IllegalArgumentException("결제 금액은 null일 수 없습니다");
        }
        if (paidAmount.isLessThan(usedPoint)) {
            // 넘으면 거스름돈이 포인트로 생긴다. DB의 CHECK 제약이 최종 방어선이지만
            // 여기서 먼저 막아야 무엇이 잘못됐는지 알 수 있다.
            throw new IllegalArgumentException(
                    "포인트 사용액이 결제 총액을 넘습니다: %s > %s".formatted(usedPoint, paidAmount));
        }
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

    /** 남은 수량에 대해 실제로 결제한 총액. */
    public Money totalPrice() {
        return totalAmount;
    }

    /**
     * 표시용 평균 단가. 누적 총액을 남은 수량으로 나눈 값이다.
     *
     * <p>가격이 다른 시점에 나눠 주문했으면 어느 한 시점의 가격도 아닌 평균이 된다.
     * 화면에 보여주기 위한 값이므로 내림해도 되지만, <b>환급 계산에는 쓰지 않는다.</b>
     * 내림된 단가에 수량을 곱하면 실제 결제액과 어긋나기 때문이다.
     */
    public Money unitPrice() {
        return quantity == 0 ? Money.ZERO : totalAmount.dividedBy(quantity);
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
        }
    }
}
