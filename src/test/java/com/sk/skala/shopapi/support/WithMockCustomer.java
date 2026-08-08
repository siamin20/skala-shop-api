package com.sk.skala.shopapi.support;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

import com.sk.skala.shopapi.customer.domain.Role;

/**
 * 우리 principal 타입으로 인증을 흉내 낸다.
 *
 * <p><b>{@code @WithMockUser}로는 이 프로젝트의 인증을 흉내 낼 수 없다.</b>
 * 그 애노테이션은 Spring Security 기본 타입인 {@code User}를 principal로 넣는데,
 * 우리 컨트롤러는 {@code @AuthenticationPrincipal AuthenticatedCustomer}로 받는다.
 * 타입이 맞지 않으면 스프링이 {@code null}을 주입하고, 결국 다음 두 가지가 벌어진다.
 *
 * <ul>
 *   <li>{@code principal.customerId()}에서 {@code NullPointerException} → 500
 *   <li>{@code AccessGuard}가 principal이 없다고 판단 → 401
 * </ul>
 *
 * <p>실제로 이 문제를 겪고 만든 애노테이션이다. 역할만 확인하는 테스트(상품 관리처럼
 * principal을 쓰지 않는 컨트롤러)에는 {@code @WithMockUser}를 그대로 써도 된다.
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
@WithSecurityContext(factory = WithMockCustomerSecurityContextFactory.class)
public @interface WithMockCustomer {

    /** 토큰 주체가 될 고객 아이디. */
    String value() default "skala01";

    /** 부여할 역할. */
    Role role() default Role.CUSTOMER;
}
