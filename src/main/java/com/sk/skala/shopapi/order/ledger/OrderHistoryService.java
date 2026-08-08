package com.sk.skala.shopapi.order.ledger;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.order.ledger.dto.OrderHistoryResponse;

import lombok.RequiredArgsConstructor;

/** 주문 내역 조회. (D43) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderHistoryService {

    private final OrderRepository orderRepository;

    public List<OrderHistoryResponse> findHistory(String customerId) {
        return orderRepository.findByCustomerWithLines(customerId).stream()
                .map(OrderHistoryResponse::from)
                .toList();
    }
}
