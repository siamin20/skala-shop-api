package com.sk.skala.shopapi.payment.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 결제 원장 저장소. (D41) */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 고객의 결제 내역. 최신순. */
    List<Payment> findByCustomer_CustomerIdOrderByPaidAtDesc(String customerId);

    /** 승인번호로 원거래를 찾는다. 카드사 대사와 환불에 쓴다. */
    Optional<Payment> findByApprovalNumber(String approvalNumber);
}
