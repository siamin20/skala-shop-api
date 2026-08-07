package com.sk.skala.shopapi.customer.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.sk.skala.shopapi.customer.app.CustomerService;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.dto.PointChargeRequest;
import com.sk.skala.shopapi.customer.dto.SignUpRequest;
import com.sk.skala.shopapi.global.common.PageResponse;
import com.sk.skala.shopapi.order.dto.OrderListResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 고객 API.
 *
 * <p>과제 명세 556~557p에 해당한다.
 *
 * <h2>명세와 다른 점</h2>
 *
 * <p>D7: 경로를 REST 규약에 맞췄다. 상품 API와 같은 규칙이다.
 *
 * <pre>
 *   GET    /api/customers/list  →  GET    /api/customers               컬렉션은 경로 자체로 표현
 *   PUT    /api/customers       →  POST   /api/customers/{id}/points   포인트는 덮어쓰기가 아니라 충전
 *   DELETE /api/customers +본문  →  DELETE /api/customers/{id}          대상을 경로로 지정
 *   POST   /api/customers       →  POST   /api/customers               가입 (동일)
 * </pre>
 *
 * <p><b>로그인은 여기에 없다.</b> 명세는 {@code POST /api/customers/login}을 두지만
 * 인증은 고객 관리와 다른 관심사다. P2에서 {@code /api/auth} 아래에 따로 만든다.
 *
 * <p>인가 규칙도 P2에서 붙는다. 지금은 목록 조회처럼 관리자만 봐야 할 엔드포인트도
 * 열려 있다. 이 PR의 범위는 기능 구현까지다.
 */
@Tag(name = "고객", description = "회원가입·조회·포인트 충전·탈퇴")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
// 쿼리 파라미터의 @Min·@Max는 @Validated가 있어야 동작한다. 빠뜨리면 조용히 무시된다.
@Validated
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 고객 목록을 페이지 단위로 조회한다.
     *
     * <p>{@code size} 상한을 두는 이유는 상품 API와 같다. 막지 않으면 한 번의 요청으로
     * 전체 테이블을 메모리에 올릴 수 있다.
     */
    @Operation(summary = "고객 목록 조회", description = "페이지 단위로 고객을 조회한다. (P2에서 관리자 전용이 된다)")
    @GetMapping
    public PageResponse<CustomerResponse> getCustomers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100을 넘을 수 없습니다") int size) {

        return customerService.getCustomers(page, size);
    }

    /**
     * 고객 정보와 주문한 상품 목록을 조회한다.
     *
     * <p>명세의 "고객 정보 + 주문 상품 리스트 반환"에 해당한다.
     * 응답에 비밀번호는 담기지 않는다. 응답 DTO에 그 자리가 없다.
     */
    @Operation(summary = "고객 상세 조회", description = "고객 정보와 주문한 상품 목록을 함께 반환한다.")
    @GetMapping("/{customerId}")
    public OrderListResponse getCustomer(@PathVariable String customerId) {
        return customerService.getCustomerWithOrders(customerId);
    }

    /**
     * 회원가입.
     *
     * <p>초기 포인트는 요청이 아니라 서버 설정({@code shop.signup.initial-point})에서 정한다.
     */
    @Operation(summary = "회원가입", description = "아이디와 비밀번호로 가입하고 초기 포인트를 지급받는다.")
    @PostMapping
    public ResponseEntity<CustomerResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        CustomerResponse created = customerService.signUp(request);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/customers/{id}").build(created.customerId()))
                .body(created);
    }

    /**
     * 포인트를 충전한다.
     *
     * <p>명세의 {@code PUT /api/customers}(포인트 덮어쓰기)를 대체한다.
     * 덮어쓰기를 허용하면 클라이언트가 자기 잔액을 원하는 값으로 설정할 수 있다.
     *
     * <p>{@code PUT}이 아니라 {@code POST}인 이유는 이 요청이 멱등하지 않기 때문이다.
     * 두 번 보내면 두 번 충전된다. {@code PUT}은 몇 번을 보내도 결과가 같아야 한다.
     */
    @Operation(summary = "포인트 충전", description = "보유 포인트에 금액을 더한다.")
    @PostMapping("/{customerId}/points")
    public CustomerResponse chargePoint(
            @PathVariable String customerId,
            @Valid @RequestBody PointChargeRequest request) {

        return customerService.chargePoint(customerId, request);
    }

    /**
     * 고객을 탈퇴시킨다.
     *
     * <p>주문 항목도 함께 삭제된다. 돌려줄 내용이 없으므로 204로 응답한다.
     */
    @Operation(summary = "회원 탈퇴", description = "고객과 그 주문 항목을 삭제한다.")
    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable String customerId) {
        customerService.deleteCustomer(customerId);
    }
}
