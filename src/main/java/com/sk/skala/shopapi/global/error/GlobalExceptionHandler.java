package com.sk.skala.shopapi.global.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 모든 컨트롤러에서 새어 나온 예외를 하나의 응답 형식으로 바꾸는 곳.
 *
 * <p>D4: 응답은 RFC 7807 {@code application/problem+json}을 따른다.
 * 표준 형식이라 클라이언트와 모니터링 도구가 따로 규약을 배우지 않아도 해석할 수 있고,
 * 상태 코드를 제대로 쓰면 프록시의 재시도·캐시 정책도 정상 동작한다.
 *
 * <p>여기에 모아두면 컨트롤러에 {@code try-catch}가 흩어지지 않는다.
 * 컨트롤러는 정상 흐름만 쓰고, 실패는 예외를 던져 이 클래스로 넘긴다.
 *
 * <p>응답 예시:
 * <pre>{@code
 * {
 *   "type": "https://skala-shop/errors/insufficient-point",
 *   "title": "포인트가 부족합니다",
 *   "status": 409,
 *   "detail": "필요 30000원, 보유 12000원",
 *   "instance": "/api/orders",
 *   "code": "INSUFFICIENT_POINT"
 * }
 * }</pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** {@code type} 필드에 넣을 문서 주소의 접두사. 실제로 열리는 페이지일 필요는 없고 식별자 역할만 한다. */
    private static final String TYPE_PREFIX = "https://skala-shop/errors/";

    /**
     * 업무 규칙 위반을 처리한다.
     *
     * <p>예상된 실패이므로 스택 트레이스를 남기지 않고 한 줄만 기록한다.
     * 포인트 부족처럼 정상 운영 중에도 계속 발생하는 상황이라, 스택을 남기면 로그가 묻힌다.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 규칙 위반: {} - {}", errorCode.name(), e.getMessage());
        return toProblemDetail(errorCode, e.getMessage());
    }

    /**
     * {@code @Valid} 검증 실패를 처리한다.
     *
     * <p>어떤 필드가 왜 틀렸는지를 {@code errors}에 필드명 → 사유로 담는다.
     * "입력값이 올바르지 않습니다"만 내려주면 클라이언트가 어느 칸을 고쳐야 할지 알 수 없다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            // 같은 필드에 규칙이 여러 개면 첫 번째 사유만 남긴다. 하나씩 고치면 되기 때문이다.
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("입력값 검증 실패: {}", errors);

        ProblemDetail problem = toProblemDetail(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * 값 객체 생성 실패처럼 코드 안쪽에서 올라온 잘못된 인자를 처리한다.
     *
     * <p>예를 들어 {@code Money.of(-1)}이 여기로 온다. 서버 버그일 수도 있어 400으로 내리되
     * 원인 파악이 되도록 스택을 남긴다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 인자", e);
        return toProblemDetail(ErrorCode.INVALID_PARAMETER, e.getMessage());
    }

    /**
     * 처리하지 못한 나머지 예외를 받는다.
     *
     * <p>원인 메시지를 응답에 담지 않는다. 예외 메시지에는 테이블명이나 쿼리처럼
     * 공격자에게 힌트가 되는 내용이 섞이기 쉽다. 상세 내용은 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toProblemDetail(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    /**
     * {@link ErrorCode}를 RFC 7807 응답 객체로 옮긴다.
     *
     * <p>{@code title}은 코드마다 고정된 요약, {@code detail}은 이번 요청에서만 유효한 설명이다.
     * {@code code}는 표준에 없는 확장 필드인데, 클라이언트가 상태 코드 대신 이 값으로 분기하도록 넣는다.
     * 상태 코드는 여러 오류가 공유하지만({@code 409}만 해도 다섯 개다) {@code code}는 하나뿐이다.
     */
    private ProblemDetail toProblemDetail(ErrorCode errorCode, String detail) {
        HttpStatus status = errorCode.getStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(errorCode.getMessage());
        problem.setType(URI.create(TYPE_PREFIX + errorCode.name().toLowerCase().replace('_', '-')));
        problem.setProperty("code", errorCode.name());
        return problem;
    }
}
