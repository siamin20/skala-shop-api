package com.sk.skala.shopapi.delivery.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 배송지 저장소. (D34) */
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {

    /** 고객의 배송지 목록. 기본 배송지가 먼저 온다. */
    List<DeliveryAddress> findByCustomer_CustomerIdOrderByIsDefaultDescIdAsc(String customerId);

    /** 결제 화면이 기본으로 고를 배송지. */
    Optional<DeliveryAddress> findFirstByCustomer_CustomerIdAndIsDefaultTrue(String customerId);

    Optional<DeliveryAddress> findByIdAndCustomer_CustomerId(Long id, String customerId);
}
