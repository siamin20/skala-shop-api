package com.sk.skala.shopapi.delivery.app;

import java.util.List;
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
 * <p>여러 개를 등록할 수 있다. 집·회사처럼 받는 곳이 나뉘는 것이 보통이고,
 * 매번 주소를 다시 치게 하는 것보다 골라 쓰게 하는 편이 낫다.
 *
 * <p>기본 배송지는 <b>하나만</b>이다. 여러 개가 기본이면 결제 화면이 어느 것을
 * 골라야 할지 알 수 없다. DB에 부분 유니크 인덱스를 걸어 최종 방어선을 뒀다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryAddressService {

    private final DeliveryAddressRepository repository;
    private final CustomerRepository customerRepository;

    public List<DeliveryAddressResponse> findAll(String customerId) {
        return repository.findByCustomer_CustomerIdOrderByIsDefaultDescIdAsc(customerId).stream()
                .map(DeliveryAddressResponse::from)
                .toList();
    }

    public Optional<DeliveryAddressResponse> findDefault(String customerId) {
        return repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                .map(DeliveryAddressResponse::from);
    }

    /**
     * 배송지를 추가한다.
     *
     * <p>첫 배송지는 자동으로 기본이 된다. 기본이 하나도 없으면 결제 화면이
     * 아무것도 고르지 못해 매번 새로 입력하게 된다.
     */
    @Transactional
    public DeliveryAddressResponse add(String customerId, DeliveryAddressRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "고객을 찾을 수 없습니다: " + customerId));

        boolean first = repository
                .findByCustomer_CustomerIdOrderByIsDefaultDescIdAsc(customerId).isEmpty();
        boolean wantsDefault = first || Boolean.TRUE.equals(request.isDefault());

        // 새 배송지를 기본으로 삼으면 기존 기본을 먼저 내려야 한다.
        // 순서를 뒤집으면 두 행이 잠시 함께 기본이 되어 유니크 인덱스에 걸린다.
        if (wantsDefault) {
            repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(previous -> previous.markDefault(false));
            repository.flush();
        }

        DeliveryAddress saved = repository.save(new DeliveryAddress(
                customer, request.label(), request.recipient(), request.phone(),
                request.zipcode(), request.address(), request.addressDetail(),
                request.entrancePassword(), wantsDefault));

        return DeliveryAddressResponse.from(saved);
    }

    /** 배송지를 고친다. 남의 배송지는 조회 단계에서 걸러진다. */
    @Transactional
    public DeliveryAddressResponse update(String customerId, Long id, DeliveryAddressRequest request) {
        DeliveryAddress address = mine(customerId, id);
        address.update(request.label(), request.recipient(), request.phone(),
                request.zipcode(), request.address(), request.addressDetail(),
                request.entrancePassword());

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(previous -> previous.markDefault(false));
            repository.flush();
            address.markDefault(true);
        }
        return DeliveryAddressResponse.from(address);
    }

    @Transactional
    public void delete(String customerId, Long id) {
        DeliveryAddress address = mine(customerId, id);
        boolean wasDefault = address.isDefault();
        repository.delete(address);
        repository.flush();

        // 기본을 지웠으면 남은 것 중 하나를 기본으로 올린다.
        // 그러지 않으면 배송지가 있는데도 결제 화면이 비어 보인다.
        if (wasDefault) {
            repository.findByCustomer_CustomerIdOrderByIsDefaultDescIdAsc(customerId).stream()
                    .findFirst()
                    .ifPresent(next -> next.markDefault(true));
        }
    }

    /**
     * 결제에서 쓰는 저장 경로.
     *
     * <p>주문서에서 넘어온 배송지를 저장한다. 이미 같은 주소가 기본으로 있으면 갱신하고,
     * 없으면 새로 만든다. 결제할 때마다 같은 주소가 쌓이면 목록이 금세 지저분해진다.
     */
    @Transactional
    public DeliveryAddressResponse saveForCheckout(String customerId, DeliveryAddressRequest request) {
        return repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId)
                .filter(existing -> existing.getZipcode().equals(request.zipcode())
                        && existing.getAddress().equals(request.address()))
                .map(existing -> update(customerId, existing.getId(), request))
                .orElseGet(() -> add(customerId, request));
    }

    private DeliveryAddress mine(String customerId, Long id) {
        return repository.findByIdAndCustomer_CustomerId(id, customerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DATA_NOT_FOUND, "배송지를 찾을 수 없습니다"));
    }
}
