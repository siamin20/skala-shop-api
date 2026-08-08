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
 * 골라야 할지 알 수 없다.
 *
 * <p>이 규칙을 DB 제약으로 걸지는 못했다. PostgreSQL이라면
 * {@code CREATE UNIQUE INDEX ... WHERE is_default = true} 한 줄이면 되는데
 * <b>H2가 부분 유니크 인덱스를 지원하지 않는다.</b> DB마다 다른 마이그레이션을 두면
 * 스키마가 두 갈래로 갈라지는데, 그 비용이 제약 하나의 값어치보다 크다고 봤다.
 * 그래서 이 서비스가 유일한 방어선이다. 기본을 세울 때 반드시 기존 기본을 내린다.
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
     * 결제에 쓸 배송지를 정한다. (D42)
     *
     * <p>배송지를 여러 개 둘 수 있게 되면서 <b>"기본 배송지로 보낸다"는 가정이 깨졌다.</b>
     * 사용자가 주문서에서 회사 주소를 골랐는데 원장에는 집 주소가 남으면,
     * 물건은 회사로 가고 기록은 집으로 남는다. 어느 쪽이 맞는지 나중에 알 방법이 없다.
     *
     * <p>그래서 결제 요청이 배송지를 지정할 수 있게 하고, 그 결정을 여기 한 곳에 모았다.
     *
     * @param addressId 주문서에서 고른 저장된 배송지. 없으면 {@code null}
     * @param request   주문서에서 새로 입력한 배송지. 없으면 {@code null}
     * @return 이 주문의 배송지. 둘 다 없고 기본 배송지도 없으면 {@code null}
     */
    @Transactional
    public DeliveryAddress resolveForCheckout(
            String customerId, Long addressId, DeliveryAddressRequest request) {

        // 1. 저장된 것을 골랐다. 그대로 쓴다.
        //    이때는 저장하지 않는다. 고른 것을 다시 저장하면 값이 덮이거나 사본이 늘어난다.
        if (addressId != null) {
            return mine(customerId, addressId);
        }

        // 2. 새로 입력했다. 저장하고 그것을 쓴다.
        if (request != null) {
            Long savedId = saveForCheckout(customerId, request).id();
            return mine(customerId, savedId);
        }

        // 3. 아무것도 안 보냈다. 기본 배송지로 둔다.
        return repository.findFirstByCustomer_CustomerIdAndIsDefaultTrue(customerId).orElse(null);
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
