package com.sk.skala.shopapi.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원가입 관련 설정.
 *
 * <p>과제 명세(552p)는 "초기 적립 포인트 세팅"이라고만 하고 금액을 정하지 않는다.
 * 시나리오 예시(530p)에서 15,000원짜리 2개를 주문한 뒤 잔액이 970,000원이므로
 * 1,000,000원으로 역산된다.
 *
 * <p>이 값을 코드에 상수로 박지 않고 설정으로 뺀 이유는, 운영에서 프로모션 등으로
 * 바뀔 수 있는 정책 값이기 때문이다. 정책이 바뀔 때마다 재빌드할 이유가 없다.
 *
 * <p>{@code spring-boot-configuration-processor}가 이 클래스로부터 메타데이터를 만들어
 * {@code application.yml}에서 자동완성이 동작한다.
 *
 * @param initialPoint 가입 시 지급할 포인트. 원 단위 정수
 */
@ConfigurationProperties(prefix = "shop.signup")
public record SignUpProperties(long initialPoint) {
}
