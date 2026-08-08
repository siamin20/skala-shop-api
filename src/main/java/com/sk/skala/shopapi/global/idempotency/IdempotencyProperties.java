package com.sk.skala.shopapi.global.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 멱등성 설정.
 *
 * @param validity 키 보관 기간. 이 시간이 지난 재시도는 새 요청으로 취급한다.
 *                 짧으면 늦은 재시도가 중복 실행되고, 길면 테이블이 커진다.
 */
@ConfigurationProperties(prefix = "shop.idempotency")
public record IdempotencyProperties(Duration validity) {
}
