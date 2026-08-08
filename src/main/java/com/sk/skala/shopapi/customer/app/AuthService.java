package com.sk.skala.shopapi.customer.app;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.customer.dto.LoginRequest;
import com.sk.skala.shopapi.customer.dto.LoginResponse;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.global.security.JwtProvider;

import lombok.RequiredArgsConstructor;

/**
 * 인증 서비스.
 *
 * <p>명세는 {@code loginCustomer}를 {@code CustomerService}에 두지만 분리했다.
 * 고객 관리(가입·조회·탈퇴)와 인증은 바뀌는 이유가 다르다. 인증 방식을 세션에서 토큰으로,
 * 토큰에서 OAuth로 바꿔도 고객 관리 로직은 그대로여야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 로그인하고 토큰을 발급한다.
     *
     * <p><b>아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.</b>
     * 구분하면 공격자가 "이 아이디는 존재한다"를 알아내 대상을 좁힐 수 있다.
     * 명세는 두 경우에 서로 다른 오류(`DATA_NOT_FOUND` / `NOT_AUTHENTICATED`)를 쓰지만
     * 그대로 두면 계정 존재 여부가 새어 나간다. (D19)
     *
     * <p>아이디가 없을 때도 <b>비밀번호 검증을 수행</b>한다. 건너뛰면 응답이 눈에 띄게 빨라져,
     * 응답 시간만으로 계정 존재 여부를 알 수 있다(타이밍 공격).
     *
     * @throws BusinessException 아이디나 비밀번호가 맞지 않으면 {@link ErrorCode#NOT_AUTHENTICATED}
     */
    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findById(request.customerId()).orElse(null);

        if (customer == null) {
            // 존재하지 않는 계정이어도 해싱 비용을 동일하게 치른다.
            // 이 한 줄이 없으면 응답 시간 차이로 아이디 존재 여부가 드러난다.
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw notAuthenticated();
        }

        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw notAuthenticated();
        }

        return new LoginResponse(
                customer.getCustomerId(),
                customer.getRole().name(),
                jwtProvider.createAccessToken(customer),
                jwtProvider.accessValiditySeconds());
    }

    /** 리프레시 토큰용 새 액세스 토큰을 만든다. */
    /**
     * 리프레시 토큰으로 액세스 토큰을 다시 발급한다.
     *
     * <p>토큰에 담긴 버전이 지금 값과 같아야 한다. 로그아웃하면 값이 올라가므로
     * 그 이전에 발급된 토큰은 여기서 거부된다. (D25)
     *
     * @param tokenVersion 리프레시 토큰에 담겨 있던 발급 시점의 버전
     */
    public LoginResponse reissue(String customerId, long tokenVersion) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(AuthService::notAuthenticated);

        if (!customer.hasTokenVersion(tokenVersion)) {
            // 로그아웃 이후에 쓰인 토큰이다.
            // 왜 거부되는지 구체적으로 알려주지 않는다. 토큰을 훔친 쪽에
            // "버전이 올라갔다"는 단서를 줄 이유가 없다.
            throw notAuthenticated();
        }

        return new LoginResponse(
                customer.getCustomerId(),
                customer.getRole().name(),
                jwtProvider.createAccessToken(customer),
                jwtProvider.accessValiditySeconds());
    }

    /** 리프레시 토큰을 만든다. 컨트롤러가 쿠키로 내보낸다. */
    /**
     * 로그아웃 처리. 토큰 버전을 올려 발급된 리프레시 토큰을 모두 무효화한다.
     *
     * <p>{@code readOnly = true}인 클래스에서 이 메서드만 쓰기 트랜잭션이다.
     * 붙이지 않으면 변경이 반영되지 않아 <b>로그아웃이 조용히 아무 일도 하지 않는다.</b>
     */
    @Transactional
    public void logout(String customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(AuthService::notAuthenticated)
                .invalidateTokens();
    }

    public String createRefreshToken(String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(AuthService::notAuthenticated);
        return jwtProvider.createRefreshToken(customer);
    }

    /**
     * 실제 BCrypt 해시 형식의 더미 값.
     *
     * <p>형식이 맞아야 {@code matches}가 실제 해싱 연산을 수행한다.
     * 아무 문자열이나 넣으면 형식 검사에서 바로 실패해 시간 차이가 그대로 남는다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static BusinessException notAuthenticated() {
        return new BusinessException(
                ErrorCode.NOT_AUTHENTICATED, "아이디 또는 비밀번호가 올바르지 않습니다");
    }
}
