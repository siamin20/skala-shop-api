package com.sk.skala.shopapi.delivery.dto;

import com.sk.skala.shopapi.delivery.domain.DeliveryAddress;

/**
 * 배송지 응답. (D34)
 *
 * <p><b>공동현관 비밀번호는 값을 싣지 않는다.</b> 등록 여부만 알려준다.
 * 이 값을 알면 건물에 들어갈 수 있어서, 목록 조회 응답에 실리면
 * 계정이 한 번 뚫렸을 때 물리적 침입까지 이어진다.
 *
 * <p>화면은 "등록됨"만 보여주고, 바꾸려면 다시 입력하게 한다.
 */
public record DeliveryAddressResponse(
        Long id,
        String label,
        String recipient,
        String phone,
        String zipcode,
        String address,
        String addressDetail,
        String fullAddress,
        boolean hasEntrancePassword,
        boolean isDefault) {

    public static DeliveryAddressResponse from(DeliveryAddress a) {
        return new DeliveryAddressResponse(
                a.getId(), a.getLabel(), a.getRecipient(), a.getPhone(), a.getZipcode(),
                a.getAddress(), a.getAddressDetail(), a.fullAddress(),
                a.hasEntrancePassword(), a.isDefault());
    }
}
