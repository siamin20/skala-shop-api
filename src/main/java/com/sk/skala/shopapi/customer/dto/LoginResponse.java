package com.sk.skala.shopapi.customer.dto;

/**
 * 로그인 응답.
 *
 * <p>D18: 액세스 토큰을 <b>응답 본문</b>으로 준다. 클라이언트는 이것을 메모리에 두고
 * {@code Authorization} 헤더로 보낸다. 쿠키에 담지 않는 이유는 브라우저가 자동으로 붙이지 않게 해
 * CSRF를 원천 차단하기 위해서다.
 *
 * <p>리프레시 토큰은 여기에 없다. {@code HttpOnly} 쿠키로만 나가므로 JS가 읽을 수 없고,
 * 따라서 XSS로 탈취되지 않는다. 두 토큰의 전달 경로를 나눈 것이 이 설계의 핵심이다.
 *
 * @param customerId  로그인한 고객 아이디
 * @param role        역할. 화면에서 관리자 메뉴 노출 여부를 정하는 데 쓴다
 * @param accessToken 액세스 토큰
 * @param expiresIn   액세스 토큰 남은 수명(초). 클라이언트가 갱신 시점을 계산한다
 */
public record LoginResponse(String customerId, String role, String accessToken, long expiresIn) {
}
