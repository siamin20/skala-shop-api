package com.sk.skala.shopapi.order.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;
import com.sk.skala.shopapi.order.app.OrderService;
import com.sk.skala.shopapi.order.dto.OrderListResponse;
import com.sk.skala.shopapi.order.dto.OrderRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
    @Operation(summary = "상품 주문", description = "포인트를 차감하고 주문 항목에 수량을 누적한다.")
    @PostMapping
    public OrderListResponse placeOrder(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @Valid @RequestBody OrderRequest request) {

        return orderService.placeOrder(principal.customerId(), request);
    }

    /**
     * 주문을 취소한다. 결제한 금액이 환급된다.
     *
     * <p>{@code DELETE}가 아니라 {@code POST}인 이유는 부분 취소가 가능하기 때문이다.
     * 수량을 지정해 일부만 취소하므로 "자원을 삭제한다"는 의미와 맞지 않는다.
     * 전량 취소로 수량이 0이 되면 항목이 사라지지만, 그건 결과이지 요청의 의미가 아니다.
     */
    @Operation(summary = "주문 취소", description = "수량만큼 취소하고 결제한 금액을 환급한다.")
    @PostMapping("/cancel")
    public OrderListResponse cancelOrder(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @Valid @RequestBody OrderRequest request) {

        return orderService.cancelOrder(principal.customerId(), request);
    }
}
