package com.sk.skala.shopapi.customer.dto;

import com.sk.skala.shopapi.customer.domain.Customer;

/**
 * 고객 응답.
 *
 * <p>담을 필드를 명시적으로 나열한다. 명세(552p)는 로그인 응답에서 "패스워드 null 처리"를
 * 요구하는데, 이는 엔티티를 그대로 내보내기 때문에 필요한 뒤처리다.
 * 응답 DTO를 쓰면 비밀번호가 애초에 들어올 자리가 없어 그 처리 자체가 사라진다(D3).
 *
 * @param customerId 고객 아이디
 * @param point      보유 포인트. 원 단위 정수
 */
public record CustomerResponse(String customerId, long point) {

    /**
     * 엔티티를 응답으로 옮긴다.
     *
     * <p>이 경계를 두는 이유는 비밀번호 해시가 응답에 섞이지 않게 하기 위해서다.
     * 엔티티를 그대로 내보내면 "패스워드를 null로 지우는" 처리를 응답마다 기억해야 하고,
     * 한 번만 빠뜨려도 해시가 유출된다. 담을 필드를 여기서 못 박으면 실수할 자리가 없다.
     */
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getCustomerId(), customer.getPoint().getAmount());
    }
}
