package com.sk.skala.shopapi.product.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 저장소.
 *
 * <p>{@link JpaRepository}를 상속하면 {@code save}, {@code findById}, {@code findAll(Pageable)},
 * {@code delete} 같은 기본 메서드가 구현 없이 제공된다. 구현 클래스는 Spring Data JPA가
 * 실행 시점에 프록시로 만들어 준다.
 *
 * <p>과제 명세(545p)는 사용자 정의 메서드로 {@code findByProductName}을 요구한다.
 * 이 프로젝트의 필드명은 {@code name}이므로 메서드 이름도 {@link #findByName(String)}이 된다.
 * 쿼리 메서드 이름은 <b>필드명</b>을 따라가지 컬럼명을 따라가지 않는다.
 * 컬럼은 명세대로 {@code product_name}이다. (D12)
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 상품명으로 조회한다. 등록·수정 시 중복 확인에 쓴다.
     *
     * <p>반환 타입이 {@code Optional}인 이유는 "없을 수 있다"가 정상 흐름이기 때문이다.
     * 신규 등록에서는 결과가 없어야 정상이고, 있으면 중복이다.
     * {@code null}을 돌려주면 호출하는 쪽이 검사를 잊어도 컴파일러가 잡아주지 못한다.
     *
     * @param name 찾을 상품명
     * @return 있으면 상품, 없으면 빈 값
     */
    Optional<Product> findByName(String name);

    /**
     * 자기 자신을 제외하고 같은 이름을 쓰는 상품이 있는지 확인한다.
     *
     * <p>수정할 때 필요하다. {@link #findByName(String)}만 쓰면 이름을 바꾸지 않고
     * 가격만 고치는 경우에도 자기 자신이 조회되어 "중복"으로 잘못 판정된다.
     *
     * @param name 확인할 상품명
     * @param id   제외할 상품 ID (수정 대상 자신)
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
