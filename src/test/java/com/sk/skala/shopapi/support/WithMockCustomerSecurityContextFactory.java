package com.sk.skala.shopapi.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;

/**
 * {@link WithMockCustomer}가 실제 인증 정보를 만드는 곳.
 *
 * <p>{@code JwtAuthenticationFilter}가 토큰을 파싱한 뒤 만드는 것과 <b>같은 형태</b>의
 * {@code Authentication}을 넣는다. 형태가 다르면 테스트는 통과하는데 실제로는 동작하지 않는
 * 상황이 생긴다.
 */
public class WithMockCustomerSecurityContextFactory
        implements WithSecurityContextFactory<WithMockCustomer> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomer annotation) {
        AuthenticatedCustomer principal =
                new AuthenticatedCustomer(annotation.value(), annotation.role());

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.role().authority())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
