package com.sk.skala.shopapi.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 계정 설정.
 *
 * <p>비밀번호에 기본값을 두지 않는다. 기본값이 있으면 설정을 잊었을 때
 * <b>모두가 아는 비밀번호로 관리자 계정이 조용히 만들어진다.</b>
 * 값이 없으면 계정을 아예 만들지 않는 편이 안전하다.
 *
 * @param id       관리자 아이디
 * @param password 평문 비밀번호. 설정되지 않으면 계정을 만들지 않는다
 */
@ConfigurationProperties(prefix = "shop.admin")
public record AdminProperties(String id, String password) {

    /** 계정을 만들 수 있을 만큼 설정이 갖춰졌는지. */
    public boolean isConfigured() {
        return id != null && !id.isBlank() && password != null && !password.isBlank();
    }
}
