package com.sk.skala.shopapi.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 등록·수정 요청. (D34)
 *
 * <p>서버에서도 검증한다. 화면이 먼저 거르지만 그것만 믿을 수는 없다.
 * 화면을 거치지 않고 API를 직접 부르면 검증이 통째로 건너뛰어진다.
 */
public record DeliveryAddressRequest(

        @NotBlank(message = "받는 분을 입력해 주세요")
        @Size(max = 50, message = "받는 분은 50자를 넘을 수 없습니다")
        String recipient,

        // 하이픈이 있어도 없어도 받는다. 사용자가 형식을 맞추게 하는 대신 서버가 받아준다.
        @NotBlank(message = "연락처를 입력해 주세요")
        @Pattern(regexp = "01[016789]-?\\d{3,4}-?\\d{4}", message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @NotBlank(message = "우편번호를 입력해 주세요")
        @Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리입니다")
        String zipcode,

        @NotBlank(message = "주소를 입력해 주세요")
        @Size(max = 200)
        String address,

        @Size(max = 100, message = "상세 주소는 100자를 넘을 수 없습니다")
        String addressDetail) {
}
