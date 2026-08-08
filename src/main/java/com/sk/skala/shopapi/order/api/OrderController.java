package com.sk.skala.shopapi.order.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.global.idempotency.IdempotentExecutor;
import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;
import com.sk.skala.shopapi.order.app.OrderService;
import com.sk.skala.shopapi.order.dto.CheckoutRequest;
import com.sk.skala.shopapi.order.dto.CheckoutResponse;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.order.dto.OrderRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 주문 API.
 *
 * <p>과제 명세 557p의 `placeOrder`·`cancelOrder`에 해당한다.
 * P1에서 미뤄둔 이유는 명세가 주문 주체를 세션에서 꺼내기 때문이다.
 * 인증이 없으면 <b>주체를 알아낼 방법 자체가 없어</b> 엔드포인트를 올바르게 정의할 수 없었다.
 *
 * <h2>주문 주체를 얻는 방법 (D6)</h2>
 *
 * <p>{@code @AuthenticationPrincipal}로 <b>토큰에서만</b> 얻는다.
 * 요청 본문이나 경로에서 받으면 남의 아이디를 보내 타인의 포인트로 주문할 수 있다.
 * {@link OrderRequest}에 {@code customerId} 필드가 아예 없는 것도 같은 이유다.
 * 필드가 없으면 실수로 그 값을 쓸 수가 없다.
 *
 * <h2>명세와 다른 경로 (D7)</h2>
 *
 * <pre>
 *   POST /api/customers/order   →  POST /api/orders
 *   POST /api/customers/cancel  →  POST /api/orders/cancel
 * </pre>
 *
 * <p>주문은 고객의 부속물이 아니라 그 자체로 조회·취소되는 자원이다.
 * 그리고 주체가 경로에 없어야 한다. {@code /api/customers/{id}/orders} 형태로 두면
 * 그 {@code id}를 신뢰하고 싶은 유혹이 생긴다.
 */
@Tag(name = "주문", description = "상품 주문·취소·조회")
@RestController
@RequestMapping("/api/orders")
// 헤더의 @NotBlank는 메서드 파라미터 검증이라 @Validated가 있어야 동작한다.
@Validated
@RequiredArgsConstructor
public class OrderController {

    /**
     * 멱등성 키 헤더 이름. 업계 관례를 따른다(Stripe·PayPal 등이 같은 이름을 쓴다).
     *
     * <p>D20: 주문과 취소는 재시도 시 중복 실행되면 포인트가 두 번 움직인다.
     * 요청 내용만으로는 재시도와 새 주문을 구분할 수 없어 키가 필요하다.
     */
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final OrderService orderService;
    private final IdempotentExecutor idempotentExecutor;

    /** 카드 결제를 포함한 주문 처리. 명세 경로는 이쪽을 쓰지 않는다. (D31, D32) */
    private final com.sk.skala.shopapi.order.app.CheckoutService checkoutService;
    private final com.sk.skala.shopapi.order.ledger.OrderHistoryService orderHistoryService;

    /**
     * 내 주문 목록을 조회한다.
     *
     * <p>경로에 고객 아이디가 없다. 토큰의 주체가 곧 조회 대상이므로
     * 남의 주문을 볼 방법이 구조적으로 없다.
     */
    @Operation(summary = "내 주문 목록", description = "로그인한 고객이 주문한 상품 목록과 잔여 포인트를 반환한다.")
    @GetMapping
    public OrderListResponse getMyOrders(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return orderService.getOrders(principal.customerId());
    }

    /**
     * 상품을 주문한다. 포인트가 차감된다.
     *
     * <p>응답으로 변경 후 <b>전체 상태</b>를 돌려준다. 바뀐 항목만 주면 클라이언트가
     * 남은 포인트와 목록을 다시 조회해야 해서 요청이 한 번 더 나간다.
     */
    @Operation(
            summary = "상품 주문",
            description = "포인트를 차감하고 주문 항목에 수량을 누적한다. Idempotency-Key 헤더가 필요하다.")
    @PostMapping
    public OrderListResponse placeOrder(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "Idempotency-Key 헤더는 필수입니다") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        return idempotentExecutor.execute(
                idempotencyKey, principal.customerId(), request, OrderListResponse.class,
                () -> orderService.placeOrder(principal.customerId(), request));
    }

    /**
     * 주문 내역. <b>일어난 일의 기록</b>이다. (D43)
     *
     * <p>{@code GET /api/orders}가 반환하는 것과 다르다. 그쪽은 명세의 모델로
     * "지금 보유한 상품"을 준다. 이쪽은 취소한 주문까지 포함한 이력이다.
     */
    @Operation(summary = "주문 내역", description = "취소한 주문을 포함한 이력을 최신순으로 반환한다.")
    @GetMapping("/history")
    public java.util.List<com.sk.skala.shopapi.order.ledger.dto.OrderHistoryResponse> history(
            @AuthenticationPrincipal AuthenticatedCustomer principal) {

        return orderHistoryService.findHistory(principal.customerId());
    }

    /**
     * 결제 수단을 지정해 주문한다. (D31, D32)
     *
     * <p>{@code POST /api/orders}가 명세의 포인트 전액 결제라면, 이쪽은 카드 결제와
     * 포인트 부분 사용을 다룬다. 경로를 나눈 이유는 명세 요청 모양을 건드리지 않기 위해서다.
     * 필드를 늘리면 명세대로 보낸 요청이 검증에 걸릴 수 있다.
     *
     * <p>적립은 <b>실제로 낸 금액(카드분)</b> 기준이다. 포인트로 낸 부분에 적립하면
     * 포인트가 포인트를 낳아 결제 없이 잔액이 늘어난다.
     */
    @Operation(
            summary = "결제 주문",
            description = "카드 결제와 포인트 부분 사용을 지원한다. 적립은 카드 결제액 기준이다.")
    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "Idempotency-Key 헤더는 필수입니다") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        return idempotentExecutor.execute(
                idempotencyKey, principal.customerId(), request, CheckoutResponse.class,
                () -> checkoutService.checkout(principal.customerId(), request));
    }

    /**
     * 주문을 취소한다. 결제한 금액이 환급된다.
     *
     * <p>{@code DELETE}가 아니라 {@code POST}인 이유는 부분 취소가 가능하기 때문이다.
     * 수량을 지정해 일부만 취소하므로 "자원을 삭제한다"는 의미와 맞지 않는다.
     * 전량 취소로 수량이 0이 되면 항목이 사라지지만, 그건 결과이지 요청의 의미가 아니다.
     */
    @Operation(
            summary = "주문 취소",
            description = "수량만큼 취소하고 결제한 금액을 환급한다. Idempotency-Key 헤더가 필요하다.")
    @PostMapping("/cancel")
    public OrderListResponse cancelOrder(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "Idempotency-Key 헤더는 필수입니다") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        return idempotentExecutor.execute(
                idempotencyKey, principal.customerId(), request, OrderListResponse.class,
                () -> orderService.cancelOrder(principal.customerId(), request));
    }
}
