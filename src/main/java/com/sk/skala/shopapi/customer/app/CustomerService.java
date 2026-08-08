package com.sk.skala.shopapi.customer.app;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.dto.PointChargeRequest;
import com.sk.skala.shopapi.customer.dto.PointUpdateRequest;
import com.sk.skala.shopapi.customer.dto.SignUpRequest;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.common.PageResponse;
import com.sk.skala.shopapi.global.config.SignUpProperties;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.order.domain.OrderItem;
import com.sk.skala.shopapi.order.domain.OrderItemRepository;
import com.sk.skala.shopapi.order.dto.OrderListResponse;

import lombok.RequiredArgsConstructor;

/**
 * 고객 관리 서비스.
 *
 * <p>과제 명세 551~552p에 해당한다. 가입·조회·포인트 충전·탈퇴를 담당한다.
 *
 * <p><b>로그인은 여기에 없다.</b> 명세는 {@code loginCustomer}를 이 서비스에 두지만,
 * 토큰 발급 없이 비밀번호만 맞춰보는 로그인은 아무 상태도 남기지 않아 쓸모가 없다.
 * 인증은 P2에서 {@code AuthService}로 따로 만들고, 이 서비스는 그때도 인증을 모른다.
 * 여기서는 가입 시 비밀번호를 해시로 바꾸는 것까지만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignUpProperties signUpProperties;

    /**
     * 고객 목록을 페이지 단위로 조회한다.
     *
     * <p>정렬 기준은 기본 키인 {@code customerId}다. 정렬을 지정하지 않으면 DB 반환 순서에
     * 의존해 페이지마다 항목이 중복되거나 누락될 수 있다.
     */
    public PageResponse<CustomerResponse> getCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "customerId"));
        Page<Customer> customers = customerRepository.findAll(pageable);
        return PageResponse.of(customers, CustomerResponse::from);
    }

    /**
     * 고객 정보와 주문한 상품 목록을 함께 조회한다.
     *
     * <p>명세 551p의 {@code getCustomerById}에 해당한다. 화면이 고객 정보와 주문 목록을
     * 따로 두 번 요청하지 않도록 한 응답에 담는다.
     *
     * <p>주문 목록은 {@code @EntityGraph}로 상품을 함께 가져온다. 지연 로딩에 맡기면
     * 주문 항목 수만큼 상품 조회 쿼리가 추가로 나간다(N+1).
     *
     * @throws BusinessException 고객이 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    public OrderListResponse getCustomerWithOrders(String customerId) {
        Customer customer = findCustomerOrThrow(customerId);
        List<OrderItem> orderItems = orderItemRepository.findByCustomer_CustomerId(customerId);
        return OrderListResponse.of(customer, orderItems);
    }

    /**
     * 회원가입.
     *
     * <p>비밀번호는 이 메서드를 지나는 순간 해시로 바뀐다. 평문은 {@link SignUpRequest} 안에서만
     * 존재하고 엔티티에도 DB에도 남지 않는다.
     *
     * <p>초기 포인트는 요청이 아니라 설정값에서 가져온다. 요청에서 받으면 클라이언트가
     * 원하는 만큼 포인트를 갖고 가입할 수 있다.
     *
     * @throws BusinessException 아이디가 이미 있으면 {@link ErrorCode#DATA_DUPLICATED}
     */
    @Transactional
    public CustomerResponse signUp(SignUpRequest request) {
        if (customerRepository.existsById(request.customerId())) {
            throw new BusinessException(
                    ErrorCode.DATA_DUPLICATED, "이미 사용 중인 아이디입니다: " + request.customerId());
        }

        Customer customer = new Customer(
                request.customerId(),
                passwordEncoder.encode(request.password()),
                Money.of(signUpProperties.initialPoint()));

        return CustomerResponse.from(customerRepository.save(customer));
    }

    /**
     * 포인트를 충전한다.
     *
     * <p>명세 552p의 {@code updateCustomer}는 포인트를 통째로 덮어쓰지만, 그러면 클라이언트가
     * 자기 잔액을 원하는 값으로 설정할 수 있다. "얼마로 바꾼다" 대신 "얼마를 더한다"로 바꿔
     * 충전 금액만 받는다. 요청 DTO가 양수만 허용하므로 잔액을 깎는 경로도 없다.
     *
     * @throws BusinessException 고객이 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    @Transactional
    public CustomerResponse chargePoint(String customerId, PointChargeRequest request) {
        Customer customer = findCustomerOrThrow(customerId);

        // 영속 엔티티이므로 값만 바꾸면 트랜잭션 종료 시 변경 감지로 UPDATE된다. save 불필요.
        customer.chargePoint(Money.of(request.amount()));

        return CustomerResponse.from(customer);
    }

    /**
     * 포인트를 특정 값으로 조정한다. 관리자 전용이다.
     *
     * <p>명세 552p의 {@code updateCustomer}에 해당한다. 이전 잔액을 무시하고 덮어쓰므로
     * 몇 번을 호출해도 결과가 같다. {@link #chargePoint} 와 달리 재시도해도 안전하다(D13).
     *
     * <p>고객 본인이 호출하면 잔액을 마음대로 설정할 수 있다. P2에서 관리자로 제한한다.
     *
     * @throws BusinessException 고객이 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    @Transactional
    public CustomerResponse updateCustomerPoint(String customerId, PointUpdateRequest request) {
        Customer customer = findCustomerOrThrow(customerId);
        customer.adjustPointTo(Money.of(request.point()));
        return CustomerResponse.from(customer);
    }

    /**
     * 고객을 탈퇴시킨다.
     *
     * <p>주문 항목을 먼저 지운다. {@code order_item}이 {@code customer}를 참조하므로
     * 고객만 지우면 외래 키 제약에 걸려 실패한다.
     *
     * <p>엔티티에 {@code cascade}를 걸지 않은 이유는, 연관관계 설정만 보고는
     * "고객을 지우면 주문도 함께 사라진다"는 사실을 알아채기 어렵기 때문이다.
     * 삭제 순서를 코드에 드러내면 무엇이 함께 지워지는지 읽는 즉시 보인다.
     *
     * @throws BusinessException 고객이 없으면 {@link ErrorCode#DATA_NOT_FOUND}
     */
    @Transactional
    public void deleteCustomer(String customerId) {
        Customer customer = findCustomerOrThrow(customerId);

        orderItemRepository.deleteAll(orderItemRepository.findByCustomer_CustomerId(customerId));

        // 자식 삭제를 DB에 먼저 반영한다. flush 없이 두면 두 삭제가 같은 시점에 나가고,
        // 실행 순서는 Hibernate의 내부 규칙에 맡겨진다. 지금은 우연히 맞지만
        // 매핑이 하나만 바뀌어도 부모가 먼저 나가 외래 키 제약에 걸릴 수 있다.
        orderItemRepository.flush();

        customerRepository.delete(customer);
    }

    /** 아이디로 고객을 찾고, 없으면 예외를 던진다. 네 메서드에서 반복되므로 한곳에 모았다. */
    private Customer findCustomerOrThrow(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));
    }
}
