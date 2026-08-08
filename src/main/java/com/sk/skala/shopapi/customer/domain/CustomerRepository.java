package com.sk.skala.shopapi.customer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 고객 저장소.
 *
 * <p>과제 명세(546p)는 "사용자 정의 메서드 필요 없음"이라고 명시한다.
 * 실제로 필요한 동작이 모두 {@link JpaRepository}의 기본 메서드로 해결된다.
 *
 * <ul>
 *   <li>가입 시 아이디 중복 확인 → {@code existsById}
 *   <li>단건 조회 → {@code findById}
 *   <li>목록 조회 → {@code findAll(Pageable)}
 *   <li>저장·삭제 → {@code save}, {@code delete}
 * </ul>
 *
 * <p>식별자 타입이 {@code String}인 이유는 {@link Customer}의 기본 키가 자동 증가 숫자가 아니라
 * 사용자가 정한 로그인 아이디이기 때문이다. 명세가 지정한 구조다.
 *
 * <p>비어 있는 인터페이스지만 남겨 둔다. 나중에 조회 조건이 생기면 여기에 붙고,
 * 무엇보다 서비스가 특정 구현이 아니라 이 타입에 의존하게 하는 자리가 된다.
 */
public interface CustomerRepository extends JpaRepository<Customer, String> {
}
