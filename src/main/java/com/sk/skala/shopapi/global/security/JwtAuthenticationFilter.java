package com.sk.skala.shopapi.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code Authorization} 헤더의 액세스 토큰을 읽어 인증 정보를 세운다.
 *
 * <p>{@link OncePerRequestFilter}를 상속하는 이유는, 서블릿 컨테이너가 포워딩이나 에러 처리로
 * 같은 요청을 여러 번 필터에 태울 수 있기 때문이다. 그때마다 토큰을 다시 파싱할 이유가 없다.
 *
 * <p><b>토큰이 없거나 잘못돼도 여기서 예외를 던지지 않는다.</b> 인증 정보를 세우지 않고 그냥 통과시키면
 * 뒤의 인가 단계가 "인증 안 됨"으로 판단해 401을 낸다. 여기서 401을 직접 쓰면
 * 로그인 없이 접근해도 되는 엔드포인트(상품 조회, 회원가입)까지 막힌다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                AuthenticatedCustomer principal = jwtProvider.parseAccessToken(token);

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,   // 자격 증명은 이미 검증됐으므로 담지 않는다
                        List.of(new SimpleGrantedAuthority(principal.role().authority())));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // 만료·위조 토큰은 정상 운영 중에도 발생한다. 스택을 남기면 로그가 묻힌다.
                log.debug("유효하지 않은 토큰: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
