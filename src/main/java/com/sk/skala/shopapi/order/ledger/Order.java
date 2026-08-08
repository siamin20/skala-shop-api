package com.sk.skala.shopapi.order.ledger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.delivery.domain.DeliveryAddress;
import com.sk.skala.shopapi.global.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문. <b>일어난 일의 기록</b>이다. (D43)
 *
 * <h2>OrderItem과 무엇이 다른가</h2>
 *
 * <table border="1">
 *   <tr><th></th><th>{@code OrderItem} (명세)</th><th>{@code Order} (여기)</th></tr>
 *   <tr><td>답하는 질문</td><td>지금 무엇을 갖고 있나</td><td>무슨 일이 있었나</td></tr>
 *   <tr><td>행의 단위</td><td>(고객, 상품) 하나씩</td><td>결제 한 번</td></tr>
 *   <tr><td>수정</td><td>수량이 오르내린다</td><td><b>만든 뒤 바꾸지 않는다</b></td></tr>
 *   <tr><td>취소하면</td><td>수량이 줄고 0이면 사라진다</td><td>취소 표시가 남는다</td></tr>
 * </table>
 *
 * <p>둘을 하나로 합치려 하면 둘 중 하나를 잃는다. 명세의 모델은 그대로 두고 이것을 덧붙였다.
 *
 * <h2>배송지를 참조하지 않고 복사한다</h2>
 *
 * <p>{@code delivery_address}를 외래 키로 걸면, 사용자가 이사해서 주소를 고쳤을 때
 * <b>과거 주문이 새 주소로 배송된 것처럼 보인다.</b> 주문 시점에 어디로 보냈는지는
 * 바뀌면 안 되는 사실이다. 상품 이름과 단가를 복사하는 것도 같은 이유다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    private static final DateTimeFormatter ORDER_NO_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Seoul"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사람이 읽고 부를 수 있는 번호.
     *
     * <p>{@code id}를 그대로 노출하면 전체 주문 수가 드러나고, 앞뒤 번호로
     * <b>남의 주문을 추측</b>할 수 있다. 날짜 + 임의 문자열로 만든다.
     */
    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "point_amount", nullable = false))
    private Money pointAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "card_amount", nullable = false))
    private Money cardAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "earned_point", nullable = false))
    private Money earnedPoint;

    // ── 배송지 스냅샷 ──
    @Column(length = 50)
    private String recipient;

    @Column(length = 20)
    private String phone;

    @Column(length = 10)
    private String zipcode;

    @Column(length = 200)
    private String address;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * 주문 항목.
     *
     * <p>{@code CascadeType.ALL}과 {@code orphanRemoval}을 쓴다. 항목은 주문 없이
     * 존재할 이유가 없어서다. 주문을 저장하면 항목도 함께 저장된다.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderLine> lines = new ArrayList<>();

    public Order(Customer customer, Money totalAmount, Money pointAmount, Money cardAmount,
            Money earnedPoint, DeliveryAddress delivery) {

        this.orderNo = generateOrderNo();
        this.customer = customer;
        this.status = OrderStatus.PAID;
        this.totalAmount = totalAmount;
        this.pointAmount = pointAmount;
        this.cardAmount = cardAmount;
        this.earnedPoint = earnedPoint;
        this.orderedAt = Instant.now();

        if (delivery != null) {
            this.recipient = delivery.getRecipient();
            this.phone = delivery.getPhone();
            this.zipcode = delivery.getZipcode();
            this.address = delivery.getAddress();
            this.addressDetail = delivery.getAddressDetail();
        }
    }

    /** 항목을 추가한다. 양쪽 참조를 함께 맞춘다. */
    public void addLine(OrderLine line) {
        lines.add(line);
        line.assignTo(this);
    }

    /**
     * 항목 하나를 부분 취소로 기록한다.
     *
     * <p>행을 지우지 않는다. <b>취소도 일어난 일이다.</b> 지우면 "산 적 없음"과
     * "샀다가 취소함"을 구분할 수 없다.
     */
    public void recordCancel(Long productId, int quantity) {
        lines.stream()
                .filter(line -> line.getProductId().equals(productId))
                .findFirst()
                .ifPresent(line -> line.cancel(quantity));

        // 전부 취소됐으면 주문 자체가 취소다. 일부만이면 부분 취소로 남긴다.
        boolean allCancelled = lines.stream().allMatch(OrderLine::isFullyCancelled);
        boolean anyCancelled = lines.stream().anyMatch(line -> line.getCancelledQuantity() > 0);

        if (allCancelled) {
            this.status = OrderStatus.CANCELLED;
            this.cancelledAt = Instant.now();
        } else if (anyCancelled) {
            this.status = OrderStatus.PARTIALLY_CANCELLED;
        }
    }

    /** 배송지 한 줄 표기. */
    public String fullAddress() {
        if (address == null) return null;
        return addressDetail == null || addressDetail.isBlank()
                ? "(%s) %s".formatted(zipcode, address)
                : "(%s) %s %s".formatted(zipcode, address, addressDetail);
    }

    /** 주문 요약. "세라마이드 수분 크림 외 2건" */
    public String summary() {
        if (lines.isEmpty()) return "주문 항목 없음";
        String first = lines.get(0).getProductName();
        return lines.size() == 1 ? first : "%s 외 %d건".formatted(first, lines.size() - 1);
    }

    private static String generateOrderNo() {
        return "%s-%s".formatted(
                ORDER_NO_DATE.format(Instant.now()),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /** 주문 상태. */
    public enum OrderStatus {
        PAID, PARTIALLY_CANCELLED, CANCELLED
    }
}
