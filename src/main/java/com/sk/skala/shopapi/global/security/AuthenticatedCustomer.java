package com.sk.skala.shopapi.global.security;

import com.sk.skala.shopapi.customer.domain.Role;

/**
 * 인증된 요청의 주체.
 *
 * <p>토큰에서 꺼낸 값만 담는다. {@code Customer} 엔티티를 principal로 쓰지 않는 이유는,
 * 그러면 요청마다 DB를 조회해야 하고 영속성 컨텍스트 밖의 엔티티가 보안 컨텍스트에
 * 오래 머무르게 되기 때문이다.
 *
 * <p>D6: 컨트롤러는 여기서만 {@code customerId}를 얻는다. 요청 본문이나 경로에서 온 값을
 * 주문 주체로 쓰면 남의 아이디로 주문할 수 있다.
 *
 * @param customerId 토큰 subject
 * @param role       토큰에 담긴 역할
 */
public record AuthenticatedCustomer(String customerId, Role role) {
}
