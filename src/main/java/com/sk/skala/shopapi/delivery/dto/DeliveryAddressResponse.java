package com.sk.skala.shopapi.delivery.dto;

import com.sk.skala.shopapi.delivery.domain.DeliveryAddress;

/** 배송지 응답. (D34) */
public record DeliveryAddressResponse(
        Long id,
        String recipient,
        String phone,
        String zipcode,
        String address,
        String addressDetail,
        String fullAddress) {

    public static DeliveryAddressResponse from(DeliveryAddress a) {
        return new DeliveryAddressResponse(
                a.getId(), a.getRecipient(), a.getPhone(), a.getZipcode(),
                a.getAddress(), a.getAddressDetail(), a.fullAddress());
    }
}
