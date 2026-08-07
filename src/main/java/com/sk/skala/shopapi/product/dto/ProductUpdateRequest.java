package com.sk.skala.shopapi.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 상품 수정 요청.
 *
 * <p>수정 대상 ID는 이 안에 두지 않고 경로 변수로 받는다(D7).
 * 본문과 경로에 ID가 둘 다 있으면 서로 다를 때 무엇을 믿을지 정해야 하는데,
 * 애초에 한 곳에만 두면 그 문제가 생기지 않는다.
 *
 * <p>등록 요청과 필드가 같지만 클래스를 나눴다. 지금은 같아 보여도 수정에서만 허용되는 항목
 * (예: 재고 조정)이 생기면 갈라지고, 그때 한쪽을 고치다 다른 쪽을 깨뜨리지 않는다.
 *
 * @param name  변경할 상품명
 * @param price 변경할 가격. 원 단위 정수
 */
public record ProductUpdateRequest(

        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 100, message = "상품명은 100자를 넘을 수 없습니다")
        String name,

        @NotNull(message = "가격은 필수입니다")
        @Positive(message = "가격은 0보다 커야 합니다")
        Long price) {
}
