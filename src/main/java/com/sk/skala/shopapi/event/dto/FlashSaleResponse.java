package com.sk.skala.shopapi.event.dto;

import java.time.Instant;

import com.sk.skala.shopapi.event.domain.FlashSale;

/**
 * 이벤트 현황 응답. (D23)
 *
 * <p>남은 수량을 노출한다. 감추면 사용자가 참여 버튼을 누른 뒤에야 품절을 알게 된다.
 */
public record FlashSaleResponse(
        Long id,
        String name,
        Long productId,
        String productName,
        long price,
        int totalQuantity,
        int remaining,
        int sold,
        Instant startsAt,
        Instant endsAt) {

    public static FlashSaleResponse from(FlashSale sale) {
        return new FlashSaleResponse(
                sale.getId(),
                sale.getName(),
                sale.getProduct().getId(),
                sale.getProduct().getName(),
                sale.getProduct().getPrice().getAmount(),
                sale.getTotalQuantity(),
                sale.getRemaining(),
                sale.soldQuantity(),
                sale.getStartsAt(),
                sale.getEndsAt());
    }
}
