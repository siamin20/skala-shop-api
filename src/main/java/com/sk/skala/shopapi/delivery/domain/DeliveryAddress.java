package com.sk.skala.shopapi.delivery.domain;

import com.sk.skala.shopapi.customer.domain.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송지. (D34)
 *
 * <p>주문이 아니라 <b>고객</b>에 붙는다. 이 프로젝트에는 주문(Order) 엔티티가 없기 때문이다.
 * 명세는 {@code OrderItem}만 두고 같은 상품을 재주문하면 수량을 누적하므로(529p)
 * "한 번의 주문"이라는 단위 자체가 없다. 실제 쇼핑몰의 <b>기본 배송지</b>에 해당한다.
 */
@Entity
@Table(name = "delivery_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 받는 사람. 주문자와 다를 수 있다. */
    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    /** 도로명 주소. 주소 검색 결과라 사용자가 직접 고치지 않는다. */
    @Column(nullable = false, length = 200)
    private String address;

    /** 동·호수. 검색으로는 알 수 없어 직접 입력한다. */
    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    /**
     * 공동현관 비밀번호. (D34)
     *
     * <p>아파트·오피스텔은 현관을 못 열면 배송이 그 자리에서 멈춘다.
     *
     * <p><b>응답에 그대로 싣지 않는다.</b> 이 값을 알면 건물에 들어갈 수 있다.
     * 등록 여부만 알려주고, 실제 값은 배송 처리에서만 쓴다.
     */
    @Column(name = "entrance_password", length = 50)
    private String entrancePassword;

    /** 배송지 별칭. "집", "회사"처럼 목록에서 구분할 이름이다. */
    @Column(nullable = false, length = 30)
    private String label;

    /**
     * 기본 배송지 여부.
     *
     * <p>고객당 하나만 참이다. 여러 개가 기본이면 결제 화면이 어느 것을 골라야 할지 모른다.
     * DB에 부분 유니크 인덱스를 걸어 최종 방어선을 뒀다.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public DeliveryAddress(Customer customer, String label, String recipient, String phone,
            String zipcode, String address, String addressDetail, String entrancePassword,
            boolean isDefault) {

        this.customer = customer;
        this.label = (label == null || label.isBlank()) ? "기본 배송지" : label;
        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.entrancePassword = entrancePassword;
        this.isDefault = isDefault;
    }

    /** 배송지를 수정한다. 새로 만들지 않는 이유는 기본 배송지가 하나로 유지되어야 해서다. */
    public void update(String label, String recipient, String phone, String zipcode,
            String address, String addressDetail, String entrancePassword) {

        this.label = (label == null || label.isBlank()) ? this.label : label;
        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.entrancePassword = entrancePassword;
    }

    /** 기본 배송지로 지정하거나 해제한다. 고객당 하나만 참이어야 한다. */
    public void markDefault(boolean value) {
        this.isDefault = value;
    }

    /**
     * 공동현관 비밀번호가 등록되어 있는지.
     *
     * <p>값 자체가 아니라 <b>등록 여부만</b> 응답에 담는다.
     * 화면은 "등록됨"만 보여주고, 바꾸려면 다시 입력하게 한다.
     */
    public boolean hasEntrancePassword() {
        return entrancePassword != null && !entrancePassword.isBlank();
    }

    /** 한 줄로 합친 주소. 주문 확인 화면에 쓴다. */
    public String fullAddress() {
        return addressDetail == null || addressDetail.isBlank()
                ? "(%s) %s".formatted(zipcode, address)
                : "(%s) %s %s".formatted(zipcode, address, addressDetail);
    }
}
