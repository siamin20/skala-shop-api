package com.sk.skala.shopapi.customer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>과제 명세의 {@code CustomerSession}에 해당한다.
 * 실제 인증 처리는 P2(Spring Security + JWT)에서 붙는다.
 *
 * <p>가입 요청과 달리 길이·형식 제약을 걸지 않는다. 로그인 실패는 어차피
 * "아이디 또는 비밀번호가 올바르지 않습니다" 하나로 응답할 것이므로,
 * 형식 검증으로 <b>존재하지 않는 아이디 형식</b>을 알려줄 이유가 없다.
 * 공격자에게 계정 규칙을 노출하지 않는 편이 낫다.
 *
 * @param customerId 로그인 아이디
 * @param password   평문 비밀번호
 */
public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다") String customerId,
        @NotBlank(message = "비밀번호는 필수입니다") String password) {
}
