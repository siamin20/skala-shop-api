package com.sk.skala.shopapi.global.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 명세 536p의 {@code PUT /api/products}, {@code DELETE /api/products}용 요청. (D27)
 *
 * <p>명세의 이 두 경로에는 경로 변수가 없다. 대상 상품을 <b>본문의 id</b>로 지정한다.
 * 교재가 엔티티를 그대로 {@code @RequestBody}로 받기 때문에 나온 형태다.
 *
 * <p>엔티티를 그대로 받지는 않는다(D3). 대신 필요한 필드만 담은 이 레코드를 쓴다.
 * {@code id}만 필수이고 나머지는 수정 요청일 때만 채운다.
 */
public record SpecProductRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long id,

        String name,

        Long price,

        Integer stock) {
}
