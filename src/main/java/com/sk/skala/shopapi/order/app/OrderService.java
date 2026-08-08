package com.sk.skala.shopapi.order.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.OrderItem;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문 서비스.
 *
 * <p>과제 명세 553p의 {@code placeOrder}, {@code cancelOrder}에 해당한다.
 *
 * <h2>고객을 인자로 받는 이유</h2>
 *
 * <p>명세는 이 안에서 {@code sessionHandler}로 로그인한 고객을 꺼낸다.
 * 여기서는 {@code customerId}를 인자로 받는다. 서비스가 "누가 요청했는지 알아내는 방법"까지
 * 알면 인증 방식이 바뀔 때마다 업무 로직을 건드려야 하고, 테스트할 때도 세션을 흉내 내야 한다.
 * 주체를 알아내는 일은 상위 계층(P2의 인증 필터)이 맡고, 여기서는 넘겨받은 주체로 규칙만 처리한다.
 *
 * <p>D6: 그 {@code customerId}는 반드시 인증 정보에서 와야 한다. 요청 본문에서 온 값을 넘기면
 * 남의 아이디로 주문할 수 있다. 그래서 {@link OrderRequest}에는 아이디 필드 자체가 없다.
 *
 * <h2>트랜잭션</h2>
 *
 * <p>주문은 포인트 차감과 주문 항목 저장을 함께 한다. 둘 중 하나만 반영되면
 * 포인트만 빠지거나 공짜 주문이 생긴다. 그래서 {@code @Transactional}로 묶는다.
 *
 * <p>동시성 제어(낙관적·비관적 락, 재시도)는 P4에서 이 클래스 위에 얹는다.
 * 지금은 트랜잭션 경계만 잡혀 있고, 같은 고객이 동시에 두 번 주문하면
 * 마지막 쓰기가 앞의 것을 덮을 수 있다. 로컬 {@code docs/04-concurrency.md} 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CustomerRepository customerRepository;

    /** 구매 적립 정책. 결제한 금액의 일정 비율을 포인트로 되돌려준다. (D31) */
    private final RewardPolicy rewardPolicy;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 고객의 주문 목록을 조회한다.
     *
     * <p>{@code CustomerService.getCustomerWithOrders}와 결과가 같다. 합치지 않은 이유는
     * 의존 방향 때문이다. 이 메서드를 고객 쪽에 두면 주문 컨트롤러가 고객 서비스를 불러야 하고,
     * 반대로 고객 쪽에서 이 메서드를 부르면 {@code customer → order} 의존이 생겨 순환이 된다.
     * 세 줄짜리 조회라 각자 두는 편이 낫다.
     *
     * @throws BusinessException 고객이 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    public OrderListResponse getOrders(String customerId) {
        return currentOrders(findCustomerOrThrow(customerId));
    }

    /**
     * 상품을 주문한다.
     *
     * <p>처리 순서는 명세와 같다. 고객·상품 조회 → 포인트 검증·차감 → 주문 항목 반영.
     *
     * <p>포인트를 먼저 차감하고 주문 항목을 나중에 만든다. 순서를 뒤집으면 포인트가 부족할 때
     * 이미 만들어진 주문 항목을 되돌려야 한다. 트랜잭션이 롤백해 주긴 하지만,
     * 실패 가능성이 있는 검사를 먼저 두는 편이 흐름을 읽기 쉽다.
     *
     * <p>같은 상품을 다시 주문하면 새 행을 만들지 않고 수량만 누적한다(명세 529p).
     * (고객, 상품) 조합에 유니크 제약이 걸려 있어 중복 행은 DB에서도 막힌다.
     *
     * @param customerId 인증으로 확인된 고객 아이디
     * @param request    주문할 상품과 수량
     * @return 주문 후의 고객 정보와 전체 주문 목록
     * @throws BusinessException 고객·상품이 없으면 {@link ErrorCode#DATA_NOT_FOUND},
     *                           포인트가 모자라면 {@link ErrorCode#INSUFFICIENT_POINT}
     */
    /**
     * 상품을 주문한다.
     *
     * <h2>락 획득 순서: Product → Customer</h2>
     *
     * <p>순서를 고정하는 것이 데드락 방지의 전부다. 주문은 Product를 먼저 잡고 Customer를
     * 건드리는데, 취소 경로가 반대로 간다면 두 트랜잭션이 서로가 쥔 행을 기다려 <b>교차
     * 데드락</b>이 난다. 한 경로만 어긋나도 발생하므로 모든 경로에서 같은 순서를 지킨다.
     *
     * <p>고객 조회를 상품보다 먼저 하면 이 순서가 깨진다. 조회 자체는 락을 잡지 않지만,
     * {@code deductPoint}로 더럽혀진 엔티티는 <b>커밋 시점</b>에 UPDATE가 나가므로
     * 실제 락 획득 시점은 커밋 순간이다. 그래서 지금 순서가 유지된다.
     * (D22, {@code docs/04-concurrency.md})
     */
    @Transactional
    public OrderListResponse placeOrder(String customerId, OrderRequest request) {
        // 명세의 기본 동작. 전액을 포인트로 낸다.
        return placeOrderWithPayment(customerId, request, null);
    }

    /**
     * 포인트 사용액을 지정해 주문한다. (D31)
     *
     * <p>{@code usePoint}가 {@code null}이면 전액 포인트 결제다. 명세의 동작이다.
     * 값이 있으면 그만큼만 포인트에서 빼고 나머지는 이미 카드로 결제된 것으로 본다.
     *
     * <p>카드 승인은 여기서 하지 않는다. 이 메서드는 트랜잭션 안에서 돌고,
     * 그 안에서 외부를 부르면 응답을 기다리는 동안 락을 붙잡는다. (D32)
     */
    @Transactional
    public OrderListResponse placeOrderWithPayment(String customerId, OrderRequest request,
            Money usePoint) {
        // 비관적 락. 재고를 읽고 검사하고 차감하는 동안 다른 요청이 끼어들지 못한다.
        // 락 없이 읽으면 두 요청이 같은 재고를 보고 둘 다 검사를 통과한다.
        Product product = lockProductOrThrow(request.productId());
        Customer customer = findCustomerOrThrow(customerId);

        // 총액은 상품의 현재 가격으로 계산한다.
        Money totalPrice = product.totalPriceOf(request.quantity());

        // 포인트로 낼 금액. 지정하지 않으면 전액이다(명세 기본).
        Money pointPortion = (usePoint == null) ? totalPrice : usePoint;

        // 재고를 먼저 줄인다. 포인트를 먼저 차감하면, 재고가 부족해 실패했을 때
        // 롤백에 기대야 한다. 롤백은 동작하지만 "실패할 것을 먼저 확인한다"는 순서가
        // 읽는 사람에게 더 분명하다.
        product.deductStock(request.quantity());

        // 포인트로 내는 만큼만 차감한다. 나머지는 카드로 이미 승인됐다. (D31)
        if (!pointPortion.isZero()) {
            customer.deductPoint(pointPortion);
        }

        // 결제 총액을 그대로 항목에 넘긴다. 항목이 상품 가격을 다시 읽으면
        // 그 사이 가격이 바뀌었을 때 결제액과 환급 재원이 어긋난다. (D15)
        //
        // 그중 포인트로 낸 금액도 함께 기록한다. 취소할 때 "포인트로 얼마를 돌려주고
        // 카드로 얼마를 환불할지" 나누려면 이 값이 필요하다. (D31)
        orderItemRepository.findByCustomerAndProduct(customer, product)
                .ifPresentOrElse(
                        existing -> existing.increase(request.quantity(), totalPrice, pointPortion),
                        () -> orderItemRepository.save(new OrderItem(
                                customer, product, request.quantity(), totalPrice, pointPortion)));

        return currentOrders(customer);
    }

    /**
     * 주문을 취소한다.
     *
     * <p>D15: 환급 재원은 현재 상품 가격이 아니라 <b>실제로 결제한 누적 총액</b>이다.
     * {@link OrderItem#cancel(int)}이 검증·차감·환급액 계산을 한 번에 처리하므로,
     * 이 메서드에서 순서를 잘못 잡아 금액이 어긋날 여지가 없다.
     *
     * <p>수량이 0이 되면 주문 항목을 지운다. 남겨두면 목록에 "0개 주문한 상품"이 보이고,
     * (고객, 상품) 유니크 제약 때문에 같은 상품을 다시 살 수도 없게 된다(명세 529p).
     *
     * @param customerId 인증으로 확인된 고객 아이디
     * @param request    취소할 상품과 수량
     * @return 취소 후의 고객 정보와 전체 주문 목록
     * @throws BusinessException 고객·상품·주문이 없으면 {@link ErrorCode#DATA_NOT_FOUND},
     *                           보유 수량보다 많이 취소하면 {@link ErrorCode#INSUFFICIENT_QUANTITY}
     */
    /**
     * 주문을 취소한다.
     *
     * <p>주문과 <b>같은 순서</b>로 락을 잡는다(Product → Customer). 취소가 재고를
     * 되돌리므로 여기서도 비관적 락이 필요하고, 순서를 뒤집으면 주문 경로와
     * 교차 데드락이 난다. (D22)
     */
    @Transactional
    public OrderListResponse cancelOrder(String customerId, OrderRequest request) {
        Product product = lockProductOrThrow(request.productId());
        Customer customer = findCustomerOrThrow(customerId);

        OrderItem orderItem = orderItemRepository.findByCustomerAndProduct(customer, product)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "주문 내역이 없습니다: " + product.getName()));

        OrderItem.Refund refund = orderItem.cancel(request.quantity());
        // 취소한 수량만큼 재고를 되돌린다. 되돌리지 않으면 취소가 반복될수록
        // 팔지도 않은 재고가 사라진다.
        product.restoreStock(request.quantity());

        // 포인트로 낸 만큼만 포인트로 돌려준다. 카드로 낸 부분은 카드로 환불된다. (D31)
        // 전액을 포인트로 돌려주면 카드로 낸 돈만큼 포인트가 늘어난다.
        customer.refundPoint(refund.pointPortion());

        // 적립도 함께 회수한다. 카드로 낸 금액에만 적립했으므로 회수도 그 기준이다.
        // 회수하지 않으면 주문과 취소를 반복해 포인트를 무한히 만들 수 있다.
        Money rewardToTake = rewardPolicy.rewardFor(refund.cardPortion());
        if (!rewardToTake.isZero()) {
            // 적립분보다 잔액이 적을 수 있다. 이미 써버린 경우다.
            // 가진 만큼만 회수한다. 음수 잔액을 만들 수는 없다.
            customer.deductPointUpTo(rewardToTake);
        }

        if (orderItem.isEmpty()) {
            orderItemRepository.delete(orderItem);
        }

        return currentOrders(customer);
    }

    /**
     * 고객의 현재 주문 목록을 조회한다.
     *
     * <p>주문·취소 응답으로 <b>변경 후 전체 상태</b>를 돌려준다. 바뀐 항목만 주면
     * 클라이언트가 남은 포인트와 목록을 다시 조회해야 해서 요청이 한 번 더 나간다.
     *
     * <p>{@code flush}를 먼저 호출하는 이유가 있다. 방금 만든 주문 항목은 아직 영속성 컨텍스트에만
     * 있어서, 곧바로 조회 쿼리를 날리면 DB에 반영되지 않은 상태로 읽혀 새 주문이 빠진다.
     */
    private OrderListResponse currentOrders(Customer customer) {
        orderItemRepository.flush();
        return OrderListResponse.of(
                customer,
                orderItemRepository.findByCustomer_CustomerId(customer.getCustomerId()));
    }

    private Customer findCustomerOrThrow(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));
    }

    /**
     * 상품 행을 비관적 락으로 잡고 조회한다.
     *
     * <p>재고를 바꾸는 경로에서만 쓴다. 단순 조회까지 락을 잡으면 상품 목록을 보는
     * 사용자들이 주문 처리를 기다리게 된다.
     */
    private Product lockProductOrThrow(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
    }
}
