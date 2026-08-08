package com.sk.skala.shopapi.delivery.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 배송지 저장소. (D34) */
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {

    /**
     * 고객의 기본 배송지.
     *
     * <p>여러 개를 등록할 수 있게 스키마를 열어뒀지만 지금은 하나만 쓴다.
     * 결제 화면이 물어보는 것은 "어디로 보낼까"이고, 대부분의 사용자에게 답은 하나다.
     */
    Optional<DeliveryAddress> findFirstByCustomer_CustomerIdAndIsDefaultTrue(String customerId);
}
