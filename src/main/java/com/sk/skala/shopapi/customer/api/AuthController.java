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
            var claims = jwtProvider.parseRefreshToken(refreshToken);
            // 토큰에 담긴 버전이 지금도 유효한지 서비스가 확인한다.
            // 로그아웃 이후에 발급된 토큰이면 여기서 걸린다. (D25)
            return authService.reissue(claims.customerId(), claims.tokenVersion());
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.NOT_AUTHENTICATED, "리프레시 토큰이 유효하지 않습니다");
        }
    }

    /**
     * 로그아웃.
     *
     * <p>두 가지를 한다. <b>쿠키를 지우는 것만으로는 로그아웃이 되지 않는다.</b>
     *
     * <ol>
     *   <li>리프레시 쿠키를 만료시킨다 — 이 브라우저에서 토큰이 사라진다
     *   <li>고객의 토큰 버전을 올린다 — <b>이미 발급된 리프레시 토큰이 전부 무효가 된다</b>
     * </ol>
     *
     * <p>두 번째가 없으면 쿠키만 지워질 뿐 토큰 자체는 그대로 유효하다.
     * 공격자가 이미 확보했다면 사용자가 로그아웃해도 <b>최대 14일 동안 계속
     * 액세스 토큰을 재발급받을 수 있다.</b> 로그아웃이 아무것도 막지 못하는 셈이다. (D25)
     *
     * <h2>남는 한계</h2>
     *
     * <p><b>액세스 토큰은 여전히 회수하지 못한다.</b> 남은 수명(최대 15분) 동안 유효하다.
     * 회수하려면 매 요청마다 DB에서 버전을 확인해야 하는데, 그러면 JWT를 쓴 이유인
     * 무상태성이 사라진다. 15분의 창을 감수하고 요청마다의 DB 조회를 피하는 선택이다.
     * 액세스 토큰을 짧게 둔 것(D18)이 이 창을 좁히는 장치다.
     *
     * <p>여러 기기에서 로그인해 있었다면 <b>모두 함께 끊긴다.</b> 기기별로 끊으려면
     * 세션 식별자가 따로 필요한데 그건 무상태 JWT의 범위를 벗어난다.
     */
    @Operation(
            summary = "로그아웃",
            description = "리프레시 쿠키를 만료시키고 발급된 리프레시 토큰을 모두 무효화한다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedCustomer principal) {

        authService.logout(principal.customerId());

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
                // HTTPS로만 전송한다. 끄면 평문 HTTP로도 쿠키가 나가서
                // 중간자가 리프레시 토큰을 그대로 가져간다. HttpOnly와 SameSite를
                // 걸어둔 의미가 사라진다.
                //
                // 기본값이 true고, HTTP로 띄우는 local 프로파일에서만 false로 내린다.
                // 하드코딩된 false였을 때는 운영에 그대로 나갈 위험이 있었다.
                .secure(jwtProperties.cookieSecure())
                .maxAge(maxAge)
                .build();
    }
}
