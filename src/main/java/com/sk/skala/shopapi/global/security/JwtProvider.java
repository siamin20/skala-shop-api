package com.sk.skala.shopapi.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급과 검증.
 *
 * <p>서명은 HS256(대칭키)을 쓴다. 발급자와 검증자가 같은 서버 하나뿐이라 비대칭키가 필요 없다.
 * 서비스가 여러 개로 나뉘어 각자 검증해야 하면 그때 RS256으로 바꾼다.
 *
 * <p>토큰에는 <b>고객 아이디와 역할만</b> 담는다. JWT 본문은 서명만 되어 있고 암호화되지 않아
 * 누구나 열어볼 수 있다. 포인트 잔액이나 이메일 같은 것을 넣으면 그대로 노출된다.
 *
 * <p>역할을 토큰에 담는 이유는 요청마다 DB를 조회하지 않기 위해서다. 대신 관리자 권한을
 * 회수해도 토큰이 만료될 때까지는 유효하다. 액세스 토큰 수명을 짧게 두는 이유 중 하나다.
 */
@Component
public class JwtProvider {

    /** 토큰 종류를 구분하는 클레임. 리프레시 토큰으로 API를 호출하는 것을 막는다. */
    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_ROLE = "role";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        // 32바이트 미만이면 Keys가 예외를 던진다. 기동 시점에 걸러야
        // 운영 중 첫 로그인에서야 발견되는 일이 없다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 액세스 토큰을 발급한다.
     *
     * <p>D18: 이 토큰은 응답 본문으로 나가 클라이언트 메모리에 보관되고 `Authorization` 헤더로 전송된다.
     * 쿠키가 아니므로 브라우저가 자동으로 붙이지 않고, 따라서 CSRF 공격이 성립하지 않는다.
     */
    public String createAccessToken(Customer customer) {
        return build(customer.getCustomerId(), customer.getRole(), TYPE_ACCESS, properties.accessValidity());
    }

    /** 리프레시 토큰을 발급한다. HttpOnly 쿠키로만 전달된다. */
    public String createRefreshToken(Customer customer) {
        return build(customer.getCustomerId(), customer.getRole(), TYPE_REFRESH, properties.refreshValidity());
    }

    private String build(String customerId, Role role, String type, java.time.Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(customerId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(validity)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 액세스 토큰을 검증하고 주체를 꺼낸다.
     *
     * @throws JwtException 서명이 틀렸거나 만료됐거나 리프레시 토큰인 경우
     */
    public AuthenticatedCustomer parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /**
     * 리프레시 토큰을 검증하고 주체를 꺼낸다.
     *
     * @throws JwtException 서명이 틀렸거나 만료됐거나 액세스 토큰인 경우
     */
    public AuthenticatedCustomer parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    /**
     * 토큰을 검증하고 주체를 꺼낸다.
     *
     * <p>서명·만료·종류를 모두 확인한다. 종류를 확인하지 않으면 수명이 긴 리프레시 토큰으로
     * API를 호출할 수 있어, 액세스 토큰을 짧게 둔 의미가 사라진다.
     *
     * <p>{@code private}인 이유는 호출부가 종류를 문자열로 넘기게 두면 오타 하나로
     * 검증이 통째로 무력화되기 때문이다. 종류별 공개 메서드를 통해서만 부르게 한다.
     */
    private AuthenticatedCustomer parse(String token, String expectedType) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "토큰 종류가 다릅니다. 기대 %s, 실제 %s".formatted(expectedType, type));
        }

        return new AuthenticatedCustomer(
                claims.getSubject(),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    /** 액세스 토큰 수명(초). 클라이언트가 재발급 시점을 계산하는 데 쓴다. */
    public long accessValiditySeconds() {
        return properties.accessValidity().toSeconds();
    }
}
