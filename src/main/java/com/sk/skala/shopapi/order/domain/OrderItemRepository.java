package com.sk.skala.shopapi.order.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.product.domain.Product;

/**
 * 주문 상품 저장소.
 *
 * <p>과제 명세 547p에 해당한다. 이름은 명세가 {@code CustomerProductRepository}와
 * {@code orderItemRepository}를 혼용하는데, 관리 대상 엔티티가 {@link OrderItem}이므로
 * 폴더 구조 설명(558p)과 같은 {@code OrderItemRepository}로 통일했다.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 특정 고객이 주문한 상품 목록을 조회한다.
     *
     * <p>명세가 설명하는 <b>속성 순회(Property Traversal)</b>다.
     * {@code Customer_CustomerId}는 "OrderItem의 customer 필드가 참조하는 엔티티의
     * customerId 값"을 뜻한다. 밑줄이 연관 엔티티로 들어가는 경계를 표시한다.
     *
     * <p>밑줄이 없으면 Spring Data가 {@code customerCustomerId}라는 필드를 먼저 찾다가
     * 없으면 스스로 경계를 추측한다. 필드명이 애매할 때 엉뚱하게 해석될 수 있으므로
     * 밑줄로 명시하는 편이 안전하다.
     *
     * <p>{@code product}를 함께 가져오는 이유는 주문 목록을 보여줄 때 상품명과 가격이
     * 반드시 필요하기 때문이다. 지연 로딩에 맡기면 항목 수만큼 추가 쿼리가 나간다(N+1).
     */
    @EntityGraph(attributePaths = "product")
    List<OrderItem> findByCustomer_CustomerId(String customerId);

    /**
     * 특정 고객이 특정 상품을 이미 주문했는지 찾는다.
     *
     * <p>주문 시 새 항목을 만들지, 기존 항목의 수량을 누적할지 판단하는 데 쓴다.
     * (고객, 상품) 조합은 유니크 제약이 걸려 있어 결과는 최대 하나다.
     */
    Optional<OrderItem> findByCustomerAndProduct(Customer customer, Product product);
}
