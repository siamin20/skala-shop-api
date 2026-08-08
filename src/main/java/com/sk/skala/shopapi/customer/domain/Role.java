package com.sk.skala.shopapi.customer.domain;

/**
 * 고객의 역할.
 *
 * <p>D17: 과제 명세에는 관리자 개념이 없다. 그래도 두는 이유는, 상품 등록·삭제나
 * 전체 고객 조회를 <b>로그인한 아무나</b> 할 수 있는 상태로는 인가를 구현했다고 할 수 없기 때문이다.
 *
 * <p>별도 {@code Admin} 엔티티로 분리하지 않았다. 그러면 인증 흐름이 둘로 갈라져
 * {@code UserDetailsService}와 로그인 엔드포인트가 각각 필요해진다. 관리자가 사실상 한 명인
 * 이 규모에서는 과하다.
 *
 * <p>Spring Security는 권한 이름에 {@code ROLE_} 접두사를 붙이는 관례를 쓴다.
 * {@code hasRole("ADMIN")}은 실제로 {@code ROLE_ADMIN} 권한을 찾으므로,
 * 권한 객체를 만들 때 {@link #authority()}로 접두사를 붙인다.
 */
public enum Role {

    /** 일반 고객. 가입하면 기본으로 부여된다. */
    CUSTOMER,

    /** 운영자. 상품 관리와 전체 고객 조회를 할 수 있다. */
    ADMIN;

    /** Spring Security가 기대하는 권한 문자열. {@code ROLE_ADMIN} 형태다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
