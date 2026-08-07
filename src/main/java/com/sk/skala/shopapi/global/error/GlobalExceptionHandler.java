package com.sk.skala.shopapi.global.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 컨트롤러에서 새어 나온 예외를 하나의 응답 형식으로 바꾸는 곳.
 *
 * <p>D4: 응답은 RFC 7807 {@code application/problem+json}을 따른다.
 * 표준 형식이라 클라이언트와 모니터링 도구가 따로 규약을 배우지 않아도 해석할 수 있고,
 * 상태 코드를 제대로 쓰면 프록시의 재시도·캐시 정책도 정상 동작한다.
 *
 * <h2>{@link ResponseEntityExceptionHandler}를 상속하는 이유</h2>
 *
 * <p>{@code @ExceptionHandler(Exception.class)} 하나만 두면 스프링이 만들어내는
 * 프레임워크 예외까지 전부 삼켜서 500으로 바꿔버린다.
 * {@code ExceptionHandlerExceptionResolver}가 {@code DefaultHandlerExceptionResolver}보다
 * 먼저 실행되기 때문이다. 그러면 아래처럼 클라이언트 잘못이 서버 장애로 둔갑한다.
 *
 * <pre>
 *   지원하지 않는 메서드      405 → 500
 *   잘못된 Content-Type    415 → 500
 *   깨진 JSON 본문          400 → 500
 *   없는 경로               404 → 500
 * </pre>
 *
 * <p>사용자는 무엇을 고쳐야 할지 알 수 없고, 5xx 알람이 잘못 울려 장애 대응이 낭비된다.
 * {@code ResponseEntityExceptionHandler}는 이 프레임워크 예외들에 대한 핸들러를 이미 갖고 있어서,
 * 상속하면 각 예외가 원래 상태 코드를 유지한 채 이 클래스를 지나간다.
 * 우리는 {@link #handleExceptionInternal}에서 응답 본문만 우리 형식으로 덧입힌다.
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
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** {@code type} 필드에 넣을 문서 주소의 접두사. 실제로 열리는 페이지일 필요는 없고 식별자 역할만 한다. */
    private static final String TYPE_PREFIX = "https://skala-shop/errors/";

    /**
     * 업무 규칙 위반을 처리한다.
     *
     * <p>예상된 실패이므로 스택 트레이스를 남기지 않고 한 줄만 기록한다.
     * 포인트 부족처럼 정상 운영 중에도 계속 발생하는 상황이라, 스택을 남기면 로그가 묻힌다.
     *
     * <p>{@link ProblemDetail}을 그대로 반환하면 스프링이 그 안의 {@code status}를
     * 응답 상태 코드로 사용한다. {@code ResponseEntity}로 감쌀 필요가 없다.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 규칙 위반: {} - {}", errorCode.name(), e.getMessage());
        return toProblemDetail(errorCode, e.getMessage());
    }

    /**
     * 쿼리 파라미터나 경로 변수의 검증 실패를 처리한다.
     *
     * <p>{@code @Valid @RequestBody}가 실패하면 {@code MethodArgumentNotValidException}이지만,
     * {@code @Validated}가 붙은 클래스의 메서드 파라미터({@code @Min}, {@code @Max} 등)가 실패하면
     * 이 예외가 온다. 둘은 별개라 따로 잡지 않으면 파라미터 검증이 500으로 나간다.
     *
     * <p>필드명은 {@code getProducts.size}처럼 메서드 이름이 앞에 붙어 오므로 마지막 마디만 남긴다.
     * 클라이언트에게 서버의 메서드 이름을 알려줄 이유가 없다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            errors.putIfAbsent(field, violation.getMessage());
        }

        log.warn("파라미터 검증 실패: {}", errors);

        ProblemDetail problem = toProblemDetail(
                ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * DB 제약 위반을 처리한다.
     *
     * <p>서비스가 저장 전에 중복을 검사해도 <b>검사와 저장 사이에 다른 요청이 끼어들면</b>
     * 유니크 제약에서 터진다. 애플리케이션 검사는 좋은 메시지를 위한 것이고,
     * 실제 최종 방어선은 DB다. 그 방어선이 작동했을 때 500을 내보내면
     * 클라이언트 잘못이 서버 장애로 둔갑하고 5xx 알람이 잘못 울린다.
     *
     * <p>이 애플리케이션에서 발생 가능한 제약 위반은 사실상 유니크 위반뿐이다.
     * 외래 키는 삭제 순서를 코드에서 지키고 있고({@code CustomerService.deleteCustomer}),
     * NOT NULL은 도메인 생성자가 먼저 막는다. 그래서 {@link ErrorCode#DATA_DUPLICATED}로 옮긴다.
     *
     * <p>원인 메시지는 응답에 담지 않는다. 제약 이름이나 테이블 구조가 노출된다.
     * 대신 로그에는 스택을 남겨 어떤 제약이 걸렸는지 추적할 수 있게 한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반", e);
        return toProblemDetail(ErrorCode.DATA_DUPLICATED, ErrorCode.DATA_DUPLICATED.getMessage());
    }

    /**
     * 값 객체 생성 실패처럼 코드 안쪽에서 올라온 잘못된 인자를 처리한다.
     *
     * <p>예를 들어 {@code Money.of(-1)}이 여기로 온다. 서버 버그일 수도 있어
     * 원인 파악이 되도록 스택을 남기되, 응답은 400으로 내린다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 인자", e);
        return toProblemDetail(ErrorCode.INVALID_PARAMETER, e.getMessage());
    }

    /**
     * 위 어디에도 걸리지 않은 예외를 받는다.
     *
     * <p>부모 클래스가 프레임워크 예외를 먼저 가져가므로, 여기까지 오는 것은
     * 정말 예상하지 못한 오류뿐이다.
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
     * {@code @Valid} 검증 실패를 처리한다.
     *
     * <p>부모 클래스가 이미 이 예외의 핸들러를 갖고 있으므로 {@code @ExceptionHandler}를
     * 새로 선언하지 않고 재정의한다. 둘 다 선언하면 매핑이 중복돼 기동에 실패한다.
     *
     * <p>어떤 필드가 왜 틀렸는지를 {@code errors}에 필드명 → 사유로 담는다.
     * "입력값이 올바르지 않습니다"만 내려주면 클라이언트가 어느 칸을 고쳐야 할지 알 수 없다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // 같은 필드에 규칙이 여러 개면 첫 번째 사유만 남긴다. 하나씩 고치면 되기 때문이다.
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("입력값 검증 실패: {}", errors);

        ProblemDetail problem = toProblemDetail(
                ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
        problem.setProperty("errors", errors);

        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    /**
     * 부모 클래스가 처리한 모든 프레임워크 예외가 마지막으로 거쳐 가는 지점.
     *
     * <p>상태 코드는 스프링이 정한 값을 그대로 두고, 응답 본문에만 우리 규약을 덧입힌다.
     * 그래야 405는 405로 나가면서도 클라이언트는 다른 오류와 똑같이 {@code code}로 분기할 수 있다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ErrorCode errorCode = resolveErrorCode(statusCode);
            problem.setTitle(errorCode.getMessage());
            problem.setType(toTypeUri(errorCode));
            problem.setProperty("code", errorCode.name());
            log.warn("프레임워크 예외: {} {} - {}", statusCode.value(), errorCode.name(), ex.getMessage());
        }

        return response;
    }

    /**
     * 스프링이 정한 상태 코드를 우리 오류 코드로 옮긴다.
     *
     * <p>프레임워크 예외에는 {@link ErrorCode}가 붙어 있지 않으므로 상태 코드로 역매핑한다.
     * 목록에 없는 상태는 4xx면 입력 문제, 그 외에는 서버 오류로 본다.
     */
    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> ErrorCode.INVALID_PARAMETER;
            case 401 -> ErrorCode.NOT_AUTHENTICATED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.DATA_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 409 -> ErrorCode.DATA_DUPLICATED;
            default -> statusCode.is4xxClientError() ? ErrorCode.INVALID_PARAMETER : ErrorCode.INTERNAL_ERROR;
        };
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
        problem.setType(toTypeUri(errorCode));
        problem.setProperty("code", errorCode.name());
        return problem;
    }

    /** {@code INSUFFICIENT_POINT} → {@code https://skala-shop/errors/insufficient-point} */
    private URI toTypeUri(ErrorCode errorCode) {
        return URI.create(TYPE_PREFIX + errorCode.name().toLowerCase().replace('_', '-'));
    }
}
