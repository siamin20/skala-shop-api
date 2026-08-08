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

    /**
     * 토큰 버전. 리프레시 토큰에만 담는다. (D25)
     *
     * <p>액세스 토큰에 담아봐야 검사하려면 매 요청 DB를 읽어야 해서
     * 무상태성이 사라진다. 재발급은 드물게 일어나므로 그때만 확인한다.
     */
    static final String CLAIM_TOKEN_VERSION = "tv";
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
        return build(customer, TYPE_ACCESS, properties.accessValidity(), null);
    }

    /**
     * 리프레시 토큰을 만든다. 발급 시점의 토큰 버전을 함께 담는다. (D25)
     *
     * <p>로그아웃으로 고객의 버전이 올라가면 이 토큰은 재발급 심사를 통과하지 못한다.
     */
    public String createRefreshToken(Customer customer) {
        return build(customer, TYPE_REFRESH, properties.refreshValidity(),
                customer.getTokenVersion());
    }

    /**
     * 토큰을 만든다.
     *
     * <p>{@code tokenVersion}이 {@code null}이면 클레임을 넣지 않는다.
     * 액세스 토큰에는 담지 않기 때문이다. 담아봐야 검사하려면 매 요청 DB를 읽어야 해서
     * 무상태성이 사라진다.
     */
    private String build(Customer customer, String type, java.time.Duration validity,
            Long tokenVersion) {

        Instant now = Instant.now();
        var builder = Jwts.builder()
                .setSubject(customer.getCustomerId())
                .claim(CLAIM_ROLE, customer.getRole().name())
                .claim(CLAIM_TYPE, type)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(validity)));

        if (tokenVersion != null) {
            builder = builder.claim(CLAIM_TOKEN_VERSION, tokenVersion);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public AuthenticatedCustomer parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /**
     * 리프레시 토큰을 검증하고 주체를 꺼낸다.
     *
     * @throws JwtException 서명이 틀렸거나 만료됐거나 액세스 토큰인 경우
     */
    /**
     * 리프레시 토큰을 검증하고 안에 담긴 정보를 꺼낸다.
     *
     * <p>토큰 버전까지 함께 돌려준다. 호출자가 DB의 현재 버전과 비교해
     * 로그아웃 이후에 발급된 것인지 판단한다. (D25)
     */
    public RefreshTokenClaims parseRefreshToken(String token) {
        AuthenticatedCustomer principal = parse(token, TYPE_REFRESH);

        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();

        Object version = claims.get(CLAIM_TOKEN_VERSION);
        if (!(version instanceof Number number)) {
            // V7 이전에 발급된 토큰이거나 변조된 것이다.
            // 통과시키면 로그아웃 무효화가 우회되므로 거부한다.
            throw new io.jsonwebtoken.MalformedJwtException("토큰 버전 클레임이 없습니다");
        }

        return new RefreshTokenClaims(
                principal.customerId(), principal.role(), number.longValue());
    }

    /** 리프레시 토큰에서 꺼낸 정보. */
    public record RefreshTokenClaims(String customerId, Role role, long tokenVersion) {
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

        return new AuthenticatedCustomer(claims.getSubject(), parseRole(claims));
    }

    /** 액세스 토큰 수명(초). 클라이언트가 재발급 시점을 계산하는 데 쓴다. */
    /**
     * 클레임의 역할 문자열을 {@link Role}로 바꾼다.
     *
     * <p>{@code Role.valueOf}를 그대로 부르면 안 된다. 모르는 값이 오면
     * {@code IllegalArgumentException}이 나는데, 이 예외는 {@link JwtException}이 아니라서
     * 필터가 "토큰이 잘못됐다"로 처리하지 못하고 <b>500 서버 오류</b>로 새어 나간다.
     *
     * <p>클라이언트가 보낸 토큰이 잘못된 것은 서버 결함이 아니라 401이어야 한다.
     * 역할 이름을 바꾸거나 지운 뒤 옛 토큰이 남아 있으면 실제로 일어나는 상황이다.
     */
    private Role parseRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null) {
            throw new io.jsonwebtoken.MalformedJwtException("역할 클레임이 없습니다");
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new io.jsonwebtoken.MalformedJwtException("알 수 없는 역할입니다: " + role);
        }
    }

    public long accessValiditySeconds() {
        return properties.accessValidity().toSeconds();
    }
}
