package com.sk.skala.shopapi.event.dto;

import java.time.Instant;

import com.sk.skala.shopapi.event.domain.FlashSale;

/**
 * 이벤트 현황 응답. (D23)
 *
 * <p>남은 수량을 노출한다. 감추면 사용자가 참여 버튼을 누른 뒤에야 품절을 알게 된다.
 *
 * <p>정가({@code listPrice})와 특가({@code price})를 함께 준다. 할인율만 주면
 * 화면이 정가를 역산해야 하고, 반올림 때문에 서버가 계산한 값과 어긋난다. (D42)
 */
public record FlashSaleResponse(
        Long id,
        String name,
        Long productId,
        String productName,
        long listPrice,
        long price,
        int discountRate,
        long saved,
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
                sale.getSalePrice().getAmount(),
                sale.discountRate(),
                sale.savedAmount().getAmount(),
                sale.getTotalQuantity(),
                sale.getRemaining(),
                sale.soldQuantity(),
                sale.getStartsAt(),
                sale.getEndsAt());
    }
}
