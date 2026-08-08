package com.sk.skala.shopapi.delivery.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.delivery.app.DeliveryAddressService;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressRequest;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressResponse;
import com.sk.skala.shopapi.global.security.AuthenticatedCustomer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 배송지 API. (D34)
 *
 * <p>경로에 고객 아이디가 없다. 토큰의 주체가 곧 대상이므로 <b>남의 배송지를 보거나
 * 고칠 방법이 구조적으로 없다.</b> 주소와 연락처는 개인정보라 특히 그렇다. (D6)
 */
@Tag(name = "배송지", description = "기본 배송지 조회와 등록")
@RestController
@RequestMapping("/api/delivery-address")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressService service;

    /**
     * 내 기본 배송지.
     *
     * <p>없으면 404가 아니라 <b>204</b>다. "아직 등록하지 않았다"는 오류가 아니라
     * 정상적인 상태다. 404로 주면 화면이 오류 처리 경로를 타게 되고,
     * 첫 방문자에게 빨간 안내가 뜬다.
     */
    @Operation(summary = "내 배송지 조회", description = "등록하지 않았으면 204를 반환한다.")
    @GetMapping
    public ResponseEntity<DeliveryAddressResponse> get(
            @AuthenticationPrincipal AuthenticatedCustomer principal) {

        return service.find(principal.customerId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "배송지 등록·수정", description = "고객당 하나를 유지한다.")
    @PutMapping
    public DeliveryAddressResponse save(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @Valid @RequestBody DeliveryAddressRequest request) {

        return service.save(principal.customerId(), request);
    }
}
