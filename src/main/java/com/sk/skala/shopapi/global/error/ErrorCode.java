package com.sk.skala.shopapi.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 이 API가 돌려주는 모든 업무 오류의 목록.
 *
 * <p>오류 코드와 HTTP 상태를 한곳에 모아두면 두 가지가 좋아진다.
 * 컨트롤러마다 상태 코드를 직접 고르지 않아도 되고, 클라이언트는 상태 코드가 아니라
 * 변하지 않는 {@code code} 문자열로 분기할 수 있다.
 *
 * <p>상태 코드를 의미대로 쓰는 이유는 D4에 있다. 과제 명세는 오류도 200으로 내려보내지만,
 * 그러면 프록시나 모니터링 도구가 성공과 실패를 구분하지 못한다.
 *
 * @see GlobalExceptionHandler
 */
@Getter
public enum ErrorCode {

    /** 입력값 검증 실패. 어떤 필드가 왜 틀렸는지는 응답 본문에 따로 담는다. */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),

    /** 토큰이 없거나 만료됐다. 로그인을 다시 해야 한다. */
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),

    /** 로그인은 되어 있으나 이 자원에 접근할 권한이 없다. */
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),

    /** 상품·고객·주문을 찾을 수 없다. */
    DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 데이터를 찾을 수 없습니다"),

    /** 상품명이나 고객 ID가 이미 있다. */
    DATA_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다"),

    /** 보유 포인트가 주문 금액보다 적다. */
    INSUFFICIENT_POINT(HttpStatus.CONFLICT, "포인트가 부족합니다"),

    /** 취소하려는 수량이 실제 주문한 수량보다 많다. */
    INSUFFICIENT_QUANTITY(HttpStatus.CONFLICT, "주문 수량이 부족합니다"),

    /** 상품 재고가 부족하다. (P4) */
    OUT_OF_STOCK(HttpStatus.CONFLICT, "상품 재고가 부족합니다"),

    /** 선착순 이벤트 수량이 모두 소진됐다. (P4) */
    SOLD_OUT(HttpStatus.CONFLICT, "선착순 수량이 모두 소진되었습니다"),

    /**
     * 낙관적 락 충돌이 재시도 횟수를 넘겼다. (P4)
     *
     * <p>서버 잘못이 아니라 동시에 같은 자원을 고치려다 밀린 것이므로 5xx가 아니라 409다.
     * 클라이언트는 그대로 다시 요청하면 된다.
     */
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 다시 시도해 주세요"),

    /** 어디에도 해당하지 않는 서버 오류. 상세 내용은 응답에 노출하지 않고 로그에만 남긴다. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
