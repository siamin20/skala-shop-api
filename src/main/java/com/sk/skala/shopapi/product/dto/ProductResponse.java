package com.sk.skala.shopapi.product.dto;

import com.sk.skala.shopapi.product.domain.Product;

/**
 * 상품 응답.
 *
 * <p>D3: 엔티티를 그대로 내려보내지 않는다. 엔티티를 응답으로 쓰면
 * 나중에 추가되는 내부 필드(재고, 낙관적 락 버전 등)가 의도치 않게 노출되고,
 * 지연 로딩 필드를 직렬화하다 트랜잭션 밖에서 예외가 나기도 한다.
 *
 * <p>{@link com.sk.skala.shopapi.global.common.Money}를 그대로 내보내지 않고
 * {@code long}으로 펼치는 이유는, 값 객체를 직렬화하면 {@code {"amount": 15000}}처럼
 * 한 겹 더 감싼 JSON이 되기 때문이다. 클라이언트에는 숫자 하나면 충분하다.
 *
 * @param id    상품 ID
 * @param name  상품명
 * @param price 판매 가격. 원 단위 정수
 */
public record ProductResponse(Long id, String name, long price, int stock,
        String category, String subcategory) {

    /**
     * 엔티티를 응답으로 옮긴다.
     *
     * <p>변환을 DTO 쪽 정적 메서드에 두는 이유는, 서비스마다 같은 매핑 코드를 반복하지 않기 위해서다.
     * 반대 방향(엔티티가 DTO를 아는 것)으로 두면 도메인이 API 표현에 묶인다.
     *
     * <p>{@code Money}를 그대로 담지 않고 {@code long}으로 펼치는 것도 여기서 처리한다.
     * 값 객체를 직렬화하면 {@code {"amount": 15000}}처럼 한 겹 더 감싼 JSON이 나간다.
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice().getAmount(),
                // 재고를 응답에 넣는다. 클라이언트가 품절을 미리 보여주지 못하면
                // 사용자는 주문 버튼을 누른 뒤에야 실패를 알게 된다.
                product.getStock(),
                product.getCategory(),
                product.getSubcategory());
    }
}
