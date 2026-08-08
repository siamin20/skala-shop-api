package com.sk.skala.shopapi.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. (D5, D18)
 *
 * <h2>시크릿에 기본값을 두지 않는다</h2>
 *
 * <p>원래 {@code application.yml}에 로컬 개발용 기본값이 박혀 있었다.
 * <b>이 저장소는 공개되어 있어 그 값을 누구나 읽을 수 있다.</b> 그대로 배포하면
 * 아무나 {@code role=ADMIN} 토큰을 만들어 서명까지 맞출 수 있다.
 * 주석으로 "운영에서 바꾸세요"라고 적어두는 것으로는 부족하다. 잊으면 그만이기 때문이다.
 *
 * <p>그래서 기본값을 없애고 <b>비어 있으면 기동을 실패시킨다.</b>
 * 로컬 개발용 값은 {@code local} 프로파일에만 둔다. 운영 프로파일에는 없으므로
 * {@code JWT_SECRET}을 주지 않으면 애초에 뜨지 않는다.
 *
 * <p>기동 시점에 막는 것이 핵심이다. 런타임에 검사하면 첫 로그인이 들어올 때까지
 * 잘못된 설정으로 서비스가 떠 있게 된다.
 *
 * @param secret          HS256 서명 키. 최소 32바이트여야 한다
 * @param accessValidity  액세스 토큰 수명. 짧게 둬야 탈취 피해가 줄어든다. 0 이하 불가
 * @param refreshValidity 리프레시 토큰 수명. 액세스 토큰보다 길어야 한다
 * @param cookieSecure    리프레시 쿠키에 {@code Secure} 속성을 붙일지 여부
 */
@ConfigurationProperties(prefix = "shop.jwt")
public record JwtProperties(
        String secret,
        Duration accessValidity,
        Duration refreshValidity,
        Boolean cookieSecure) {

    /** HS256이 요구하는 최소 키 길이. 더 짧으면 jjwt가 예외를 던진다. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    JWT_SECRET이 설정되지 않았습니다.

                    32바이트 이상의 임의 문자열을 환경변수로 지정하세요.
                      export JWT_SECRET="$(openssl rand -base64 48)"

                    기본값을 두지 않는 이유: 저장소에 커밋된 시크릿이 운영에 적용되면
                    누구나 관리자 토큰을 위조할 수 있습니다.
                    로컬 개발은 local 프로파일에 준비된 값이 자동으로 쓰입니다.""");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            // 문자 수가 아니라 바이트 수로 센다. 한글은 한 글자가 3바이트라
            // 글자 수로 재면 짧은 키를 통과시킨다. (D14와 같은 함정이다)
            throw new IllegalStateException(
                    "JWT_SECRET이 너무 짧습니다. %d바이트인데 최소 %d바이트가 필요합니다"
                            .formatted(length, MIN_SECRET_BYTES));
        }

        // 수명도 같은 자리에서 막는다.
        //
        // 비워두면 null이 그대로 바인딩되고, 첫 로그인에서 now.plus(null)이
        // NullPointerException을 던져 500으로 실패한다. 기동은 멀쩡히 되므로
        // 배포 후 첫 사용자가 밟기 전까지 아무도 모른다.
        //
        // 이 클래스의 목적이 "잘못된 설정으로 서비스가 떠 있게 두지 않는다"인데
        // 시크릿만 막고 수명을 빠뜨리면 절반만 지킨 셈이다.
        requirePositive(accessValidity, "shop.jwt.access-validity");
        requirePositive(refreshValidity, "shop.jwt.refresh-validity");

        if (accessValidity.compareTo(refreshValidity) > 0) {
            // 액세스 토큰이 리프레시보다 오래 살면 갱신 구조 자체가 뒤집힌다.
            // 짧은 수명으로 탈취 피해를 줄이려던 의도(D18)가 사라진다.
            throw new IllegalStateException(
                    "액세스 토큰 수명(%s)이 리프레시 토큰 수명(%s)보다 깁니다"
                            .formatted(accessValidity, refreshValidity));
        }

        if (cookieSecure == null) {
            // 기본값을 true로 둔다. 설정을 빠뜨렸을 때 안전한 쪽으로 기울어야 한다.
            // primitive boolean이었다면 누락 시 false가 되어 반대로 기울었을 것이다.
            cookieSecure = true;
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null) {
            throw new IllegalStateException("%s가 설정되지 않았습니다".formatted(name));
        }
        if (value.isZero() || value.isNegative()) {
            // 0이면 발급 즉시 만료된다. 음수면 발급 시점부터 이미 만료다.
            // 둘 다 "설정은 있는데 아무도 로그인할 수 없는" 상태를 만든다.
            throw new IllegalStateException(
                    "%s는 0보다 커야 합니다. 현재 %s".formatted(name, value));
        }
    }
}
