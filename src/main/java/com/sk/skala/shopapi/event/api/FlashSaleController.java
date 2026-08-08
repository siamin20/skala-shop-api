package com.sk.skala.shopapi.event.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.event.app.FlashSaleService;
import com.sk.skala.shopapi.event.dto.FlashSaleOrderRequest;
import com.sk.skala.shopapi.event.dto.FlashSaleResponse;
import com.sk.skala.shopapi.global.idempotency.IdempotentExecutor;
import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;
import com.sk.skala.shopapi.order.dto.OrderListResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 선착순 이벤트 API. (D23)
 *
 * <p>목록과 상세는 공개다. 비로그인 방문자도 어떤 이벤트가 열리는지 볼 수 있어야
 * 참여할 마음이 생긴다. 참여만 인증을 요구한다.
 *
 * <p>참여에 {@code Idempotency-Key}를 요구하는 이유는 일반 주문과 같다. (D20)
 * 오히려 여기가 더 절실하다. 선착순은 사용자가 <b>연타하는 것이 정상 행동</b>이라
 * 중복 요청이 예외가 아니라 기본값이다.
 */
@Tag(name = "선착순 이벤트", description = "한정 수량 이벤트 조회와 참여")
@RestController
@RequestMapping("/api/flash-sales")
@Validated
@RequiredArgsConstructor
public class FlashSaleController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final FlashSaleService flashSaleService;
    private final IdempotentExecutor idempotentExecutor;

    @Operation(summary = "이벤트 목록", description = "등록된 선착순 이벤트와 남은 수량을 반환한다.")
    @GetMapping
    public List<FlashSaleResponse> getFlashSales() {
        return flashSaleService.getFlashSales();
    }

    @Operation(summary = "이벤트 상세", description = "이벤트 하나의 현황을 반환한다.")
    @GetMapping("/{id}")
    public FlashSaleResponse getFlashSale(@PathVariable Long id) {
        return flashSaleService.getFlashSale(id);
    }

    /**
     * 이벤트에 참여한다.
     *
     * <p>참여 주체는 토큰에서만 온다. 요청 본문에 {@code customerId}를 넣어도 무시된다.
     * 선착순은 남의 이름으로 참여할 유인이 특히 큰 기능이다. (D6)
     */
    @Operation(
            summary = "이벤트 참여",
            description = "선착순 수량을 차감하고 주문을 만든다. Idempotency-Key 헤더가 필요하다.")
    @PostMapping("/orders")
    public OrderListResponse purchase(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "Idempotency-Key 헤더는 필수입니다")
            String idempotencyKey,
            @Valid @RequestBody FlashSaleOrderRequest request) {

        return idempotentExecutor.execute(
                idempotencyKey, principal.customerId(), request, OrderListResponse.class,
                () -> flashSaleService.purchase(principal.customerId(), request));
    }
}
