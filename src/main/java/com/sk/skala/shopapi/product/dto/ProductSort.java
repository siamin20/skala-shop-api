package com.sk.skala.shopapi.product.dto;

/**
 * 상품 목록 정렬 기준. (D33)
 *
 * <p>문자열 정렬 파라미터를 그대로 받지 않는다. Spring Data의 {@code Sort}를 그대로
 * 열어주면 클라이언트가 <b>아무 필드로나 정렬할 수 있다.</b> 존재하지 않는 필드를 보내면
 * 500이 나고, 인덱스 없는 필드를 보내면 전체 스캔이 돈다.
 *
 * <p>열거형으로 두면 허용한 것만 들어온다. 잘못된 값은 스프링이 400으로 막는다.
 */
public enum ProductSort {

    /** 최신 등록순. 기본값이다. */
    LATEST,

    /** 판매량순. 집계가 필요해 별도 쿼리를 쓴다. */
    BEST,

    /** 가격 낮은순. */
    PRICE_ASC,

    /** 가격 높은순. */
    PRICE_DESC,

    /** 이름순. */
    NAME
}
