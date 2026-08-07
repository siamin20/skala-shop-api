package com.sk.skala.shopapi.global.error;

import lombok.Getter;

/**
 * 업무 규칙을 어겼을 때 던지는 예외.
 *
 * <p>도메인과 서비스는 HTTP를 모른 채 이 예외만 던지고,
 * 상태 코드로 옮기는 일은 {@link GlobalExceptionHandler}가 혼자 맡는다.
 * 덕분에 도메인 코드에 {@code ResponseEntity}나 상태 코드가 섞이지 않는다.
 *
 * <p>{@code RuntimeException}을 상속하는 이유는 두 가지다.
 * 검사 예외로 만들면 호출부마다 {@code throws}가 번져 나가고,
 * 무엇보다 Spring의 {@code @Transactional}이 기본적으로 비검사 예외에만 롤백하기 때문이다.
 * 포인트를 깎다가 실패했는데 롤백되지 않으면 데이터가 깨진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 오류 코드의 기본 메시지로 예외를 만든다.
     *
     * @param errorCode 어떤 규칙을 어겼는지
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 상황을 덧붙여 예외를 만든다.
     *
     * <p>{@code detail}은 응답의 {@code detail} 필드로 그대로 나간다.
     * "필요 30000원, 보유 12000원"처럼 사용자가 다음에 뭘 해야 할지 알 수 있는 내용을 담는다.
     * 반대로 내부 구조를 짐작하게 하는 값(쿼리, 스택, 내부 ID)은 넣지 않는다.
     *
     * @param errorCode 어떤 규칙을 어겼는지
     * @param detail    사용자에게 보여줄 구체적인 상황
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
