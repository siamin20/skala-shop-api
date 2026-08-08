package com.sk.skala.shopapi.global.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 명세 536p의 {@code PUT /api/customers}용 요청. (D27)
 *
 * <p>경로 변수가 없어 대상 고객을 <b>본문의 customerId</b>로 지정한다.
 *
 * <p>바꿀 수 있는 것은 포인트뿐이다. 비밀번호 변경은 명세에 없고,
 * 아이디는 기본 키라 바꿀 수 없다.
 */
public record SpecCustomerRequest(

        @NotBlank(message = "고객 ID는 필수입니다")
        String customerId,

        @NotNull(message = "포인트는 필수입니다")
        @PositiveOrZero(message = "포인트는 0 이상이어야 합니다")
        Long point) {
}
