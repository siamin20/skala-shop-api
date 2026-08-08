package com.sk.skala.shopapi.order.ledger;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 주문 원장 저장소. (D43) */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 고객의 주문 내역. 최신순.
     *
     * <p>{@code join fetch}로 항목을 함께 읽는다. 없으면 주문 20건에 쿼리가 21번 나간다(N+1).
     * {@code distinct}가 필요한 이유는 조인 결과가 항목 수만큼 부풀기 때문이다.
     */
    @Query("""
            select distinct o from Order o
            left join fetch o.lines
            where o.customer.customerId = :customerId
            order by o.orderedAt desc
            """)
    List<Order> findByCustomerWithLines(String customerId);

    Optional<Order> findByOrderNoAndCustomer_CustomerId(String orderNo, String customerId);
}
