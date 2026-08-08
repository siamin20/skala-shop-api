package com.sk.skala.shopapi.order.ledger;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.delivery.domain.DeliveryAddress;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.payment.domain.Payment;
import com.sk.skala.shopapi.payment.domain.PaymentRepository;
import com.sk.skala.shopapi.product.domain.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문과 결제를 기록한다. (D41, D43)
 *
 * <p>업무 처리(재고 차감, 포인트 차감)와 <b>기록</b>을 나눠 뒀다.
 * 업무 로직 안에 기록 코드가 섞이면 명세가 정의한 흐름을 읽기 어려워진다.
 *
 * <h2>기록 실패가 결제를 되돌리면 안 되는가</h2>
 *
 * <p>같은 트랜잭션 안에 둔다. 기록에 실패하면 결제도 롤백된다.
 * <b>돈은 나갔는데 기록이 없는 상태보다, 아예 없던 일로 만드는 편이 낫다.</b>
 * 카드 승인은 이미 났으므로 상위에서 승인 취소가 이어진다(D32).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLedger {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 주문 한 건과 그 결제를 남긴다.
     *
     * @param lines    주문 항목. (상품, 수량, 결제액, 포인트 사용액)
     * @param delivery 이 주문의 배송지. 호출자가 정해서 넘긴다. 없으면 {@code null}
     */
    @Transactional
    public Order record(Customer customer, List<Line> lines, Money pointAmount, Money cardAmount,
            Money earnedPoint, PaymentMethod method, String approvalNumber, String maskedCard,
            DeliveryAddress delivery) {

        Money total = pointAmount.plus(cardAmount);

        // 배송지를 여기서 다시 찾지 않는다. (D42)
        //
        // 예전에는 이 자리에서 '기본 배송지'를 조회했다. 배송지가 하나뿐일 때는 맞았지만,
        // 여러 개를 두면 사용자가 주문서에서 고른 것과 달라진다. 물건은 회사로 가고
        // 기록은 집으로 남는 상태가 된다. 어느 배송지로 보냈는지는 주문을 받은 쪽이 아는 사실이므로
        // 원장이 추측하지 않고 넘겨받는다.
        Order order = new Order(customer, total, pointAmount, cardAmount, earnedPoint, delivery);
        for (Line line : lines) {
            order.addLine(new OrderLine(
                    line.product(), line.product().getPrice(), line.quantity(),
                    line.lineAmount(), line.usedPoint()));
        }
        orderRepository.save(order);

        Payment payment = new Payment(customer, total, pointAmount, cardAmount, earnedPoint,
                approvalNumber, maskedCard, method, order.summary());
        payment.assignTo(order);
        paymentRepository.save(payment);

        log.info("주문 기록. orderNo={}, total={}, method={}",
                order.getOrderNo(), total.getAmount(), method);

        return order;
    }

    /**
     * 취소를 기록한다.
     *
     * <p>가장 최근에 그 상품을 산 주문부터 취소로 표시한다. 여러 주문에 걸쳐 같은 상품을
     * 샀을 때 어느 것을 취소하는지 정해야 하는데, 사용자가 주문을 지정하지 않기 때문이다.
     *
     * <p>명세의 취소 API가 <b>상품과 수량만</b> 받는 구조라 이렇게 된다(536p).
     * 주문번호를 받는 취소 API를 따로 두는 편이 정확하지만 명세를 벗어난다.
     */
    @Transactional
    public void recordCancel(String customerId, Long productId, int quantity) {
        List<Order> orders = orderRepository.findByCustomerWithLines(customerId);

        int remaining = quantity;
        for (Order order : orders) {
            if (remaining <= 0) break;

            for (OrderLine line : order.getLines()) {
                if (!line.getProductId().equals(productId) || line.isFullyCancelled()) continue;

                int cancellable = Math.min(line.activeQuantity(), remaining);
                order.recordCancel(productId, cancellable);
                remaining -= cancellable;
                break;
            }
        }

        if (remaining > 0) {
            // 원장에 남은 수량보다 많이 취소됐다. 원장이 유실됐거나 원장 도입 전 주문이다.
            // 업무 처리는 이미 끝났으므로 실패시키지 않고 기록만 남긴다.
            log.warn("취소 수량이 주문 원장보다 많다. customerId={}, productId={}, 남은={}",
                    customerId, productId, remaining);
        }
    }

    /** 주문 항목 하나. */
    public record Line(Product product, int quantity, Money lineAmount, Money usedPoint) {
    }
}
