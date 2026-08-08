package com.sk.skala.shopapi.event.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 선착순 이벤트 저장소. 전략마다 다른 조회·갱신 방법을 제공한다. (D23)
 */
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    /**
     * 비관적 락으로 행을 잡고 조회한다. {@code SELECT ... FOR UPDATE}가 나간다.
     *
     * <p>같은 행을 노리는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 멈춘다.
     * 정확하지만 <b>모든 요청이 한 줄로 서므로</b> 처리량이 대기 시간에 좌우된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FlashSale s where s.id = :id")
    Optional<FlashSale> findByIdForUpdate(@Param("id") Long id);

    /**
     * 조건부 원자 UPDATE로 수량을 차감한다. (D23)
     *
     * <p>네 전략 중 가장 단순하면서 자주 간과되는 방법이다.
     * <b>읽고-검사하고-쓰는 세 단계를 한 문장으로 합친다.</b>
     *
     * <pre>
     *   UPDATE flash_sale SET remaining = remaining - ? WHERE id = ? AND remaining &gt;= ?
     * </pre>
     *
     * <p>{@code remaining >= ?} 조건이 재고 검사를 대신한다. 검사와 차감 사이에 다른
     * 요청이 끼어들 틈이 없다. DB가 UPDATE 대상 행에 자동으로 락을 걸고 조건을 다시
     * 평가하기 때문이다. <b>애플리케이션이 값을 읽지 않으므로 갱신 유실이 성립하지 않는다.</b>
     *
     * <p>비관적 락과 비슷해 보이지만 다르다. 비관적 락은 SELECT 시점부터 커밋까지 행을
     * 붙잡지만, 이 방식은 UPDATE 문장이 실행되는 순간만 잡는다. 잡는 구간이 짧다.
     *
     * <p>대신 잃는 것이 있다. 실패해도 <b>왜 실패했는지 알 수 없다.</b>
     * 0행이 반환되면 "수량 부족"인지 "그런 이벤트가 없음"인지 구분되지 않아
     * 호출부가 따로 조회해 판단해야 한다.
     *
     * @return 갱신된 행 수. 0이면 수량이 부족했거나 이벤트가 없다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FlashSale s
               set s.remaining = s.remaining - :quantity
             where s.id = :id
               and s.remaining >= :quantity
            """)
    int decreaseRemaining(@Param("id") Long id, @Param("quantity") int quantity);

    /** 취소 보상. 처음 수량을 넘지 않도록 조건을 함께 건다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FlashSale s
               set s.remaining = s.remaining + :quantity
             where s.id = :id
               and s.remaining + :quantity <= s.totalQuantity
            """)
    int increaseRemaining(@Param("id") Long id, @Param("quantity") int quantity);
}
