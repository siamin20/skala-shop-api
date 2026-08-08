package com.sk.skala.shopapi.global.security;

import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.customer.domain.Role;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

/**
 * 본인 여부 확인.
 *
 * <p>{@code SecurityConfig}의 필터 체인은 "로그인했는가"와 "역할이 무엇인가"까지만 판단한다.
 * <b>"이 요청자가 그 대상의 주인인가"는 판단할 수 없다.</b> 경로 변수의 값과 토큰 주체를
 * 비교해야 하는데, 필터 체인은 경로 패턴만 보기 때문이다.
 *
 * <p>그래서 {@code /api/customers/{customerId}} 같은 엔드포인트는 여기서 한 번 더 확인한다.
 * 이 검사가 없으면 <b>로그인한 아무나 남의 주문 내역을 보고 남의 계정을 탈퇴시킬 수 있다.</b>
 *
 * <p>컨트롤러마다 {@code if}를 쓰지 않고 한곳에 모은 이유는, 흩어져 있으면
 * 새 엔드포인트를 추가할 때 검사를 빠뜨려도 아무도 모르기 때문이다.
 */
@Component
public class AccessGuard {

    /**
     * 본인이거나 관리자인지 확인한다.
     *
     * <p>관리자를 통과시키는 이유는 고객 지원 업무 때문이다. 문의가 들어왔을 때
     * 관리자가 해당 고객의 주문 내역을 볼 수 없으면 확인해줄 방법이 없다.
     *
     * @param principal  토큰에서 얻은 요청자
     * @param customerId 접근하려는 대상
     * @throws BusinessException 본인도 관리자도 아니면 {@link ErrorCode#ACCESS_DENIED}
     */
    public void requireSelfOrAdmin(AuthenticatedCustomer principal, String customerId) {
        if (principal == null) {
            // 필터 체인이 먼저 막으므로 정상 경로에서는 도달하지 않는다.
            // 인가 설정이 잘못돼 열린 경우를 대비한 방어선이다.
            throw new BusinessException(ErrorCode.NOT_AUTHENTICATED);
        }

        if (principal.role() == Role.ADMIN) {
            return;
        }

        if (!principal.customerId().equals(customerId)) {
            // 어느 계정에 접근하려 했는지 응답에 담지 않는다.
            // 담으면 존재하는 아이디를 확인하는 수단이 된다.
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "본인의 정보만 조회할 수 있습니다");
        }
    }
}
