package com.sk.skala.shopapi.product.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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
     * 판매량이 많은 순으로 상품을 조회한다. (D33)
     *
     * <p>랭킹을 화면에서 만들 수 없어 서버에 둔다. 프론트가 가진 것은 상품 목록뿐이고
     * <b>누가 얼마나 팔렸는지는 주문 데이터에만 있다.</b>
     *
     * <p>{@code LEFT JOIN}이다. {@code INNER JOIN}으로 하면 <b>한 번도 안 팔린 상품이
     * 목록에서 사라진다.</b> 신상품이 랭킹 화면에서 아예 보이지 않게 되는 셈이다.
     *
     * <p>{@code COALESCE}로 판매량 없는 상품을 0으로 만든다. 없으면 null이 되어
     * 정렬 순서가 DB 설정에 좌우된다(PostgreSQL은 null을 가장 크게 본다).
     * 그러면 안 팔린 상품이 맨 앞에 오는 랭킹이 된다.
     *
     * <p>집계 쿼리라 페이지 처리를 위해 count 쿼리를 따로 준다.
     * 지정하지 않으면 Spring Data가 group by가 섞인 count 쿼리를 만들어 실패한다.
     */
    @Query(value = """
            select p from Product p
            left join OrderItem oi on oi.product = p
            group by p
            order by coalesce(sum(oi.quantity), 0) desc, p.id asc
            """,
            countQuery = "select count(p) from Product p")
    Page<Product> findAllOrderBySales(Pageable pageable);

    /**
     * 재고를 바꾸기 위해 상품 행을 <b>비관적 락</b>으로 잡고 조회한다. (D22)
     *
     * <p>{@code SELECT ... FOR UPDATE}가 나간다. 같은 행을 노리는 다른 트랜잭션은
     * 이 트랜잭션이 끝날 때까지 그 지점에서 멈춘다.
     *
     * <h2>왜 낙관적 락이 아닌가</h2>
     *
     * <p>인기 상품은 하나의 행에 요청이 몰리는 hot row가 된다. 낙관적 락은
     * <b>충돌이 드물다는 전제</b> 위에서 쓰는 전략이라 이 상황에는 맞지 않는다.
     * 200개 요청이 같은 행을 다투면 대부분이 충돌하고, 재시도해도 또 충돌한다.
     * 재시도가 부하를 더 키우는 쪽으로 작동한다.
     *
     * <h2>타임아웃은 왜 힌트로 걸지 않는가</h2>
     *
     * <p>{@code jakarta.persistence.lock.timeout} 힌트를 쓰지 않았다.
     * PostgreSQL의 {@code FOR UPDATE}에는 <b>대기 시간을 지정하는 문법이 없다.</b>
     * {@code NOWAIT}(0)과 {@code SKIP LOCKED}만 있어서 "3초만 기다린다"를 표현할 수 없다.
     * 힌트를 적어두면 걸려 있다고 착각하기 쉬운데 실제로는 무시된다.
     *
     * <p>대신 세션 설정 {@code lock_timeout}으로 건다.
     * {@code application.yml}의 {@code connection-init-sql}에서 커넥션마다 한 번 적용한다.
     *
     * <h2>호출 순서</h2>
     *
     * <p>락 획득 순서는 항상 <b>Product → Customer</b>다. 뒤집는 경로가 하나라도
     * 생기면 주문과 취소가 서로를 기다리는 교차 데드락이 난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

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

    /** 대분류로 거른 목록. (D35) */
    Page<Product> findByCategory(String category, Pageable pageable);

    /** 대분류와 소분류로 거른 목록. */
    Page<Product> findByCategoryAndSubcategory(String category, String subcategory, Pageable pageable);

    /** 등록된 대분류·소분류 목록. 화면의 탭을 이 값으로 만든다. */
    @Query("select distinct p.category, p.subcategory from Product p order by p.category, p.subcategory")
    java.util.List<Object[]> findCategories();
}
