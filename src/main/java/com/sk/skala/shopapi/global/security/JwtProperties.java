package com.sk.skala.shopapi.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정.
 *
 * <p>비밀키를 코드에 두지 않고 설정으로 뺀다. 코드에 박으면 저장소에 그대로 올라가고,
 * 저장소를 읽을 수 있는 사람은 누구나 임의의 토큰을 만들어 아무 계정으로도 로그인할 수 있다.
 *
 * @param secret          서명 키. HS256은 최소 32바이트를 요구한다
 * @param accessValidity  액세스 토큰 수명. 짧게 두어 탈취 시 피해 시간을 줄인다 (D18)
 * @param refreshValidity 리프레시 토큰 수명
 */
@ConfigurationProperties(prefix = "shop.jwt")
public record JwtProperties(String secret, Duration accessValidity, Duration refreshValidity) {
}
