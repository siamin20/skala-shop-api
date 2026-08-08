package com.sk.skala.shopapi.customer.api;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import com.sk.skala.shopapi.customer.app.AuthService;
import com.sk.skala.shopapi.customer.dto.LoginRequest;
import com.sk.skala.shopapi.customer.dto.LoginResponse;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;
import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;
import com.sk.skala.shopapi.global.security.JwtProvider;
import com.sk.skala.shopapi.global.security.JwtProperties;

import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 인증 API.
 *
 * <p>명세는 로그인을 {@code POST /api/customers/login}에 두지만 {@code /api/auth} 아래로 옮겼다(D7).
 * 인증은 고객 관리와 다른 관심사이고, 토큰 갱신·로그아웃이 함께 묶여야 하기 때문이다.
 *
 * <h2>토큰 전달 방식 (D18)</h2>
 *
 * <pre>
 *   액세스 토큰   응답 본문 → 클라이언트 메모리 → Authorization 헤더    (15분)
 *   리프레시 토큰  HttpOnly 쿠키, SameSite=Strict, Path=/api/auth/refresh  (2주)
 * </pre>
 *
 * <p>둘의 전달 경로를 나눈 것이 핵심이다. 액세스 토큰은 브라우저가 자동으로 붙이지 않으므로
 * <b>CSRF가 성립하지 않고</b>, 리프레시 토큰은 JS가 읽을 수 없으므로 <b>XSS로 탈취되지 않는다</b>.
 * 액세스 토큰이 메모리에만 있어 새로고침하면 사라지지만, 리프레시 쿠키로 즉시 재발급된다.
 */
@Tag(name = "인증", description = "로그인·토큰 갱신·로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 쿠키 이름. 명세 531p의 {@code bff-access}를 따르되 리프레시 토큰에 쓴다. */
    private static final String REFRESH_COOKIE = "bff-refresh";

    /**
     * 리프레시 쿠키가 전송될 경로.
     *
     * <p>이 경로로 보내는 요청에만 쿠키가 붙는다. 일반 API 호출에는 아예 실리지 않으므로
     * 토큰이 네트워크에 노출되는 횟수가 크게 줄어든다.
     */
    private static final String REFRESH_PATH = "/api/auth/refresh";

    private final AuthService authService;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    /** 로그인. 액세스 토큰은 본문으로, 리프레시 토큰은 쿠키로 나간다. */
    @Operation(summary = "로그인", description = "액세스 토큰을 본문으로, 리프레시 토큰을 HttpOnly 쿠키로 발급한다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        String refreshToken = authService.createRefreshToken(response.customerId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, jwtProperties.refreshValidity()).toString())
                .body(response);
    }

    /**
     * 액세스 토큰을 재발급한다.
     *
     * <p>쿠키가 없으면 401을 낸다. 이 경로는 인증 없이 열려 있으므로
     * 토큰 검증을 여기서 직접 한다.
     */
    @Operation(summary = "토큰 갱신", description = "리프레시 쿠키로 새 액세스 토큰을 발급한다.")
    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.NOT_AUTHENTICATED, "리프레시 토큰이 없습니다");
        }

        try {
            AuthenticatedCustomer principal = jwtProvider.parseRefreshToken(refreshToken);
            return authService.reissue(principal.customerId());
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.NOT_AUTHENTICATED, "리프레시 토큰이 유효하지 않습니다");
        }
    }

    /**
     * 로그아웃.
     *
     * <p>리프레시 쿠키를 만료시킨다. 액세스 토큰은 서버가 회수할 수 없다.
     * JWT는 그 자체로 유효성을 담고 있어 발급 후에는 무효화할 방법이 없기 때문이다.
     * 남은 수명(15분) 동안은 유효하며, 이것이 액세스 토큰을 짧게 두는 또 하나의 이유다.
     * 즉시 무효화가 필요하면 서버에 폐기 목록을 둬야 하는데, 그러면 JWT의 장점인
     * 무상태성이 사라진다.
     */
    @Operation(summary = "로그아웃", description = "리프레시 쿠키를 만료시킨다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    /** 현재 로그인한 고객. 클라이언트가 새로고침 후 상태를 복원할 때 쓴다. */
    @Operation(summary = "내 정보", description = "토큰에 담긴 고객 아이디와 역할을 반환한다.")
    @GetMapping("/me")
    public AuthenticatedCustomer me(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return principal;
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                // JS가 읽을 수 없다. XSS가 있어도 토큰을 훔쳐갈 수 없다.
                .httpOnly(true)
                // 다른 사이트에서 시작된 요청에는 붙지 않는다. CSRF 방어의 한 겹.
                .sameSite("Strict")
                // 이 경로 외에는 전송되지 않는다.
                .path(REFRESH_PATH)
                // 운영에서는 HTTPS 전용이어야 한다. 로컬 HTTP 개발을 위해 설정으로 뺄 수도 있으나
                // 지금은 false로 두고 P6 배포 시 프로파일로 분리한다.
                .secure(false)
                .maxAge(maxAge)
                .build();
    }
}
