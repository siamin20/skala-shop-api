package com.sk.skala.shopapi.delivery.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * 고칠 방법이 구조적으로 없다.</b> 주소·연락처·공동현관 비밀번호는 개인정보다. (D6)
 */
@Tag(name = "배송지", description = "배송지 목록·등록·수정·삭제")
@RestController
@RequestMapping("/api/delivery-addresses")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressService service;

    @Operation(summary = "내 배송지 목록", description = "기본 배송지가 먼저 온다.")
    @GetMapping
    public List<DeliveryAddressResponse> list(
            @AuthenticationPrincipal AuthenticatedCustomer principal) {
        return service.findAll(principal.customerId());
    }

    @Operation(summary = "배송지 추가", description = "첫 배송지는 자동으로 기본이 된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryAddressResponse add(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return service.add(principal.customerId(), request);
    }

    @Operation(summary = "배송지 수정")
    @PutMapping("/{id}")
    public DeliveryAddressResponse update(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long id,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return service.update(principal.customerId(), id, request);
    }

    @Operation(summary = "배송지 삭제", description = "기본을 지우면 남은 것 중 하나가 기본이 된다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long id) {
        service.delete(principal.customerId(), id);
        return ResponseEntity.noContent().build();
    }
}
