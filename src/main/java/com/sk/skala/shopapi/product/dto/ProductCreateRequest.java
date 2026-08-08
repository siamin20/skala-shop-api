package com.sk.skala.shopapi.product.dto;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.product.domain.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 상품 등록 요청.
 *
 * <p>D3: 명세는 {@code @RequestBody Product}로 엔티티를 직접 받지만 쓰지 않는다.
 * 엔티티를 그대로 바인딩하면 클라이언트가 {@code id}처럼 서버가 정해야 할 값을 보낼 수 있다.
 * 명세가 "신규 Product의 ID는 0L로 세팅"이라고 적은 것도 이 문제를 우회하려는 것인데,
 * 요청 DTO에 {@code id}를 두지 않으면 그런 처리 자체가 필요 없어진다.
 *
 * <p>{@code record}를 쓰는 이유는 불변이고 보일러플레이트가 없기 때문이다.
 * 요청 DTO는 만들어진 뒤 값이 바뀔 일이 없다.
 *
 * @param name  상품명
 * @param price 판매 가격. 원 단위 정수 (D1)
 */
public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 100, message = "상품명은 100자를 넘을 수 없습니다")
        String name,

        // @Positive라 0도 거부된다. 명세의 "가격 0이면 오류" 규칙과 일치한다.
        @NotNull(message = "가격은 필수입니다")
        @Positive(message = "가격은 0보다 커야 합니다")
        Long price,

        /*
         * 초기 재고. 명세에 없는 확장 필드라 생략할 수 있게 둔다. (D22)
         *
         * 생략하면 0이다. 재고를 다루지 않던 기존 요청이 그대로 통하되, 그렇게 만든
         * 상품은 주문할 수 없다. "말하지 않은 재고는 없는 재고"가 임의의 기본값을
         * 주는 것보다 예측 가능하다.
         *
         * 가격과 달리 @PositiveOrZero다. 재고 0인 상품(입고 대기, 품절)은 정상적인
         * 상태라 등록 자체를 막을 이유가 없다. 가격 0원인 상품과는 성격이 다르다.
         */
        @PositiveOrZero(message = "재고는 0 이상이어야 합니다")
        Integer stock) {

    /** 검증을 통과한 요청을 도메인 객체로 옮긴다. 변환 책임을 서비스에서 덜어낸다. */
    public Product toEntity() {
        return new Product(name, Money.of(price), stock == null ? 0 : stock);
    }
}
