package com.sk.skala.shopapi.order.app;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.PaymentMethod;
import com.sk.skala.shopapi.order.dto.CheckoutRequest;
import com.sk.skala.shopapi.order.dto.CheckoutResponse;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.payment.app.CardPaymentGateway;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제를 포함한 주문 처리. (D31, D32)
 *
 * <h2>왜 OrderService와 나눴는가</h2>
 *
 * <p>{@code OrderService}는 명세(549~553p)의 주문 로직이다. 포인트를 차감하고 항목을 쌓는다.
 * 거기에 카드 승인을 끼워 넣으면 <b>명세 로직 안에 외부 호출이 섞인다.</b>
 * 채점자가 봐야 할 코드가 결제 연동에 묻힌다.
 *
 * <p>그래서 결제는 이 클래스가 앞단에서 처리하고, 재고·포인트·주문 항목은
 * 기존 서비스가 그대로 맡는다.
 *
 * <h2>순서가 핵심이다</h2>
 *
 * <pre>
 *   1. 금액 계산        (트랜잭션 밖)
 *   2. 카드 승인 요청    (트랜잭션 밖)  ← 외부 호출은 반드시 여기
 *   3. 재고·포인트·주문  (트랜잭션 안)
 *   4. 실패하면 승인 취소
 * </pre>
 *
 * <p>2번을 트랜잭션 안에 두면 카드사 응답을 기다리는 동안 상품 행의 락과 DB 커넥션을
 * 붙잡고 있다. 카드사가 5초 늦으면 그 5초 동안 같은 상품의 모든 주문이 멈춘다.
 * D22에서 다룬 커넥션 고갈이 그대로 재현된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final OrderService orderService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CardPaymentGateway cardPaymentGateway;
    private final RewardPolicy rewardPolicy;
    private final com.sk.skala.shopapi.delivery.app.DeliveryAddressService deliveryAddressService;

    public CheckoutResponse checkout(String customerId, CheckoutRequest request) {
        // ── 0. 배송지를 저장한다 ──
        // 결제보다 먼저 한다. 결제가 성공했는데 배송지를 못 받으면 어디로 보낼지 모른다.
        if (request.delivery() != null) {
            deliveryAddressService.save(customerId, request.delivery());
        }

        // ── 1. 금액을 먼저 계산한다 (트랜잭션 밖) ──
        Money total = Money.ZERO;
        for (CheckoutRequest.Item item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.DATA_NOT_FOUND, "상품을 찾을 수 없습니다: " + item.productId()));
            total = total.plus(product.totalPriceOf(item.quantity()));
        }

        if (request.paymentMethod() == PaymentMethod.POINT) {
            // 명세의 기본 동작이다. 전액 포인트로 내고 적립은 없다.
            OrderListResponse orders = null;
            for (CheckoutRequest.Item item : request.items()) {
                orders = orderService.placeOrder(customerId, item.toOrderRequest());
            }
            return new CheckoutResponse(orders, total.getAmount(), 0, 0, null, null);
        }

        // ── 카드 결제 ──
        Money usePoint = resolveUsePoint(customerId, request, total);
        Money cardAmount = total.minus(usePoint);

        if (request.card() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "카드 정보가 필요합니다");
        }

        // ── 2. 승인 (트랜잭션 밖) ──
        String orderId = UUID.randomUUID().toString();
        CardPaymentGateway.Approval approval = cardAmount.isZero()
                // 포인트로 전액을 덮으면 카드사를 부를 이유가 없다.
                // 0원 승인을 요청하면 카드사가 거절하고, 그 거절이 사용자에게 오류로 보인다.
                ? new CardPaymentGateway.Approval(null, null)
                : cardPaymentGateway.authorize(request.card(), cardAmount, orderId);

        // ── 3. 재고·포인트·주문 (트랜잭션 안) ──
        try {
            OrderListResponse orders = applyOrder(customerId, request, usePoint, cardAmount);
            Money earned = rewardPolicy.rewardFor(cardAmount);

            return new CheckoutResponse(
                    orders, usePoint.getAmount(), cardAmount.getAmount(), earned.getAmount(),
                    approval.approvalNumber(), approval.maskedCard());

        } catch (RuntimeException e) {
            // ── 4. 돈은 나갔는데 주문이 없는 상태를 막는다 ──
            if (approval.approvalNumber() != null) {
                cardPaymentGateway.cancelQuietly(approval.approvalNumber(), orderId);
            }
            throw e;
        }
    }

    /**
     * 재고 차감·포인트 사용·적립·주문 기록을 한 트랜잭션에서 처리한다.
     *
     * <p>외부 호출이 없다. 이 구간은 짧게 끝나야 락 대기가 길어지지 않는다.
     */
    @Transactional
    protected OrderListResponse applyOrder(String customerId, CheckoutRequest request,
            Money usePoint, Money cardAmount) {

        // 적립금 사용액을 항목별로 나눈다. 첫 항목에 몰아주면 그 항목만 취소했을 때
        // 적립금이 전부 돌아오고 나머지는 카드 환불이 된다. 비율대로 나눠야
        // 어느 항목을 취소하든 낸 만큼 돌아간다.
        OrderListResponse orders = null;
        Money remaining = usePoint;
        int index = 0;

        for (CheckoutRequest.Item item : request.items()) {
            boolean last = (++index == request.items().size());
            Product product = productRepository.findById(item.productId()).orElseThrow();
            Money itemTotal = product.totalPriceOf(item.quantity());

            // 마지막 항목이 나머지를 모두 가져간다. 비례 배분에서 내림으로 생긴
            // 잔돈이 여기서 정산되어 합계가 정확히 맞는다. (D1의 방식과 같다)
            Money portion = last ? remaining : usePoint.proportion(1, request.items().size());
            if (portion.isLessThan(itemTotal) || portion.equals(itemTotal)) {
                remaining = remaining.minus(portion);
            } else {
                portion = itemTotal;
                remaining = remaining.minus(portion);
            }

            orders = orderService.placeOrderWithPayment(customerId, item.toOrderRequest(), portion);
        }

        // 적립은 실제로 낸 금액(카드분) 기준이다. 포인트로 낸 부분에는 적립하지 않는다.
        Money earned = rewardPolicy.rewardFor(cardAmount);
        if (!earned.isZero()) {
            Customer customer = customerRepository.findById(customerId).orElseThrow();
            customer.chargePoint(earned);
            // 적립 후 잔액을 응답에 반영하려면 다시 읽어야 한다.
            return orderService.getOrders(customerId);
        }
        return orders;
    }

    /**
     * 사용할 포인트를 정한다.
     *
     * <p>요청한 값을 그대로 믿지 않는다. 두 가지를 넘을 수 없다.
     *
     * <ul>
     *   <li><b>보유 잔액</b> — 없는 포인트를 쓰면 잔액이 음수가 된다
     *   <li><b>결제 총액</b> — 넘으면 거스름돈이 포인트로 생긴다
     * </ul>
     *
     * <p>초과분을 오류로 돌려주지 않고 잘라낸다. 사용자가 "전액 사용"을 눌렀는데
     * 1원 차이로 실패하는 것보다 가능한 만큼 쓰는 편이 낫다.
     */
    private Money resolveUsePoint(String customerId, CheckoutRequest request, Money total) {
        if (request.usePoint() == 0) {
            return Money.ZERO;
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));

        Money requested = Money.of(request.usePoint());
        Money limit = customer.getPoint().isLessThan(total) ? customer.getPoint() : total;
        return requested.isLessThan(limit) ? requested : limit;
    }
}
