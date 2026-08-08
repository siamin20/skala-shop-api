package com.sk.skala.shopapi.payment.domain;

import java.time.Instant;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.order.domain.PaymentMethod;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 결제 원장. (D41)
 *
 * <p>카드 승인을 받아놓고 기록을 남기지 않고 있었다. 승인번호가 응답에만 담기고 버려져서
 * <b>환불할 때 원거래를 지정할 수 없었다.</b> 카드사는 승인번호로 취소를 받는다.
 *
 * <h2>한계 — 주문에 걸지 못한다</h2>
 *
 * <p>명세는 {@code OrderItem}만 두고 {@code (고객, 상품)} 유니크로 수량을 누적한다(529p).
 * <b>"한 번의 주문"이라는 단위가 존재하지 않아</b> 결제를 걸 자리가 없다.
 *
 * <p>그래서 고객에 걸고 무엇을 샀는지는 {@code summary}에 요약해 둔다.
 * 정석은 order → order_line → payment지만, 그러려면 명세의 모델을 갈아엎어야 한다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    /** 적립금으로 낸 금액. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "point_amount", nullable = false))
    private Money pointAmount;

    /** 카드로 낸 금액. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "card_amount", nullable = false))
    private Money cardAmount;

    /** 이 결제로 적립된 금액. 취소할 때 얼마를 회수할지 알려면 남겨야 한다. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "earned_point", nullable = false))
    private Money earnedPoint;

    /** 카드사 승인번호. 포인트 전액 결제면 null이다. */
    @Column(name = "approval_number", length = 40)
    private String approvalNumber;

    /** 마스킹된 카드번호. 평문은 저장하지 않는다. (D32) */
    @Column(name = "masked_card", length = 30)
    private String maskedCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** 무엇을 샀는지 요약. 주문 엔티티가 없어 항목을 걸 자리가 없다. */
    @Column(nullable = false, length = 200)
    private String summary;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * 이 결제가 속한 주문. (D43)
     *
     * <p>처음에는 주문 엔티티가 없어 결제를 고객에만 걸었다. 주문 원장을 만들면서
     * 연결했다. 이제 "이 주문이 어떻게 결제됐나"에 답할 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private com.sk.skala.shopapi.order.ledger.Order order;

    public Payment(Customer customer, Money totalAmount, Money pointAmount, Money cardAmount,
            Money earnedPoint, String approvalNumber, String maskedCard,
            PaymentMethod method, String summary) {

        this.customer = customer;
        this.totalAmount = totalAmount;
        this.pointAmount = pointAmount;
        this.cardAmount = cardAmount;
        this.earnedPoint = earnedPoint;
        this.approvalNumber = approvalNumber;
        this.maskedCard = maskedCard;
        this.method = method;
        this.status = PaymentStatus.PAID;
        this.summary = summary;
        this.paidAt = Instant.now();
    }

    /**
     * 결제를 취소 상태로 바꾼다.
     *
     * <p>행을 지우지 않는다. <b>취소도 기록이다.</b> 지우면 "결제된 적 없음"과
     * "결제했다가 취소함"을 구분할 수 없고, 카드사 명세서와 대사할 수도 없다.
     */
    /** 주문에 연결한다. 주문을 먼저 저장한 뒤 부른다. */
    public void assignTo(com.sk.skala.shopapi.order.ledger.Order order) {
        this.order = order;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    /** 결제 상태. */
    public enum PaymentStatus {
        PAID, CANCELLED
    }
}
