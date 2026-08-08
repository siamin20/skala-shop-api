package com.sk.skala.shopapi.delivery.app;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.delivery.domain.DeliveryAddress;
import com.sk.skala.shopapi.delivery.domain.DeliveryAddressRepository;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressRequest;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressResponse;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 배송지 관리. (D34)
 *
 * <p>고객당 하나를 유지한다. 여러 개를 담을 수 있게 스키마는 열어뒀지만,
 * 결제 화면이 묻는 것은 "어디로 보낼까"이고 대부분의 사용자에게 답은 하나다.
 * 여러 배송지 관리는 화면이 복잡해지는 만큼의 값을 못 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryAddressService {

    private final DeliveryAddressRepository repository;
    private final CustomerRepository customerRepository;

    public Optional<DeliveryAddressResponse> find(String customerId) {
        return repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                .map(DeliveryAddressResponse::from);
    }

    /**
     * 배송지를 저장한다. 있으면 고치고 없으면 만든다.
     *
     * <p>새로 만들기만 하면 결제할 때마다 배송지가 쌓이고, 그중 어느 것이 기본인지
     * 알 수 없게 된다. 고객당 하나를 유지하는 편이 단순하다.
     */
    @Transactional
    public DeliveryAddressResponse save(String customerId, DeliveryAddressRequest request) {
        DeliveryAddress address = repository
                .findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                .map(existing -> {
                    existing.update(request.recipient(), request.phone(), request.zipcode(),
                            request.address(), request.addressDetail());
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = customerRepository.findById(customerId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));
                    return repository.save(new DeliveryAddress(
                            customer, request.recipient(), request.phone(), request.zipcode(),
                            request.address(), request.addressDetail()));
                });

        return DeliveryAddressResponse.from(address);
    }
}
