package com.sk.skala.shopapi.event.app;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.event.domain.FlashSale;
import com.sk.skala.shopapi.event.domain.FlashSaleRepository;
import com.sk.skala.shopapi.event.dto.FlashSaleOrderRequest;
import com.sk.skala.shopapi.event.dto.FlashSaleResponse;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.OrderItem;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * 선착순 이벤트 참여 처리. (D23)
 *
 * <h2>락 획득 순서: FlashSale → Product → Customer</h2>
 *
 * <p>P4-A에서 정한 Product → Customer 앞에 FlashSale이 붙는다. 이 순서를 모든 경로에서
 * 지켜야 교차 데드락이 나지 않는다. 일반 주문이 Product부터 잡고 이벤트 주문이
 * Product를 나중에 잡는 것은 문제가 없다. <b>공통으로 등장하는 자원들의 상대 순서가
 * 같으면</b> 순환이 생기지 않기 때문이다.
 *
 * <h2>왜 재고도 함께 줄이는가</h2>
 *
 * <p>이벤트 수량은 상품 재고의 일부를 떼어둔 것이다. 이벤트로 팔린 만큼 상품 재고도
 * 줄지 않으면 <b>같은 물건이 두 번 팔린다.</b> 이벤트 수량이 소진돼도 일반 판매로
 * 계속 살 수 있게 되기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final FlashSaleStrategies strategies;
    private final FlashSaleProperties properties;

    public List<FlashSaleResponse> getFlashSales() {
        return flashSaleRepository.findAll().stream().map(FlashSaleResponse::from).toList();
    }

    public FlashSaleResponse getFlashSale(Long id) {
        return FlashSaleResponse.from(findOrThrow(id));
    }

    /** 설정된 전략으로 참여한다. 운영 경로는 항상 이쪽을 쓴다. */
    public OrderListResponse purchase(String customerId, FlashSaleOrderRequest request) {
        return purchase(customerId, request, properties.strategy());
    }

    /**
     * 전략을 지정해 참여한다.
     *
     * <p><b>네 방식을 같은 조건에서 비교 측정하기 위해 열어둔 통로다.</b>
     * 컨트롤러는 이 오버로드를 쓰지 않는다. 전략은 운영 결정이지 요청마다 바뀔 값이 아니다.
     */
    @Transactional
    public OrderListResponse purchase(String customerId, FlashSaleOrderRequest request,
            FlashSaleStrategy strategyType) {

        FlashSale sale = findOrThrow(request.flashSaleId());

        // 기간 검사를 수량 차감보다 먼저 한다. 뒤에 하면 끝난 이벤트에서도 수량이
        // 줄었다가 롤백되는데, 그 사이 다른 요청이 품절을 보게 된다.
        sale.validateOpen(Instant.now());

        // ① 이벤트 수량. 전략마다 다른 방식으로 이 한 줄을 지킨다.
        strategies.get(strategyType).claim(request.flashSaleId(), request.quantity());

        // ② 상품 재고. 여기서부터는 일반 주문과 같은 순서다. (D22)
        Product product = productRepository.findByIdForUpdate(sale.getProduct().getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "상품을 찾을 수 없습니다"));
        product.deductStock(request.quantity());

        // ③ 고객 포인트.
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));

        Money totalPrice = product.totalPriceOf(request.quantity());
        customer.deductPoint(totalPrice);

        orderItemRepository.findByCustomerAndProduct(customer, product)
                .ifPresentOrElse(
                        existing -> existing.increase(request.quantity(), totalPrice),
                        () -> orderItemRepository.save(
                                new OrderItem(customer, product, request.quantity())));

        orderItemRepository.flush();
        return OrderListResponse.of(
                customer, orderItemRepository.findByCustomer_CustomerId(customerId));
    }

    /**
     * 이벤트를 시작할 준비를 한다.
     *
     * <p>Redis 전략만 실제로 할 일이 있다. 카운터를 미리 채워두지 않으면 첫 요청이
     * "카운터 없음"으로 실패한다. DB 전략은 아무것도 하지 않는다.
     */
    @Transactional
    public void prepare(Long flashSaleId, FlashSaleStrategy strategyType) {
        FlashSale sale = findOrThrow(flashSaleId);
        strategies.get(strategyType).prepare(flashSaleId, sale.getRemaining());
    }

    private FlashSale findOrThrow(Long id) {
        return flashSaleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "이벤트를 찾을 수 없습니다: " + id));
    }
}
