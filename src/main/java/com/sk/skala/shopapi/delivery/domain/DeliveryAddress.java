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

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public DeliveryAddress(Customer customer, String recipient, String phone,
            String zipcode, String address, String addressDetail) {

        this.customer = customer;
        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.isDefault = true;
    }

    /** 배송지를 수정한다. 새로 만들지 않는 이유는 기본 배송지가 하나로 유지되어야 해서다. */
    public void update(String recipient, String phone, String zipcode,
            String address, String addressDetail) {

        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
    }

    /** 한 줄로 합친 주소. 주문 확인 화면에 쓴다. */
    public String fullAddress() {
        return addressDetail == null || addressDetail.isBlank()
                ? "(%s) %s".formatted(zipcode, address)
                : "(%s) %s %s".formatted(zipcode, address, addressDetail);
    }
}
