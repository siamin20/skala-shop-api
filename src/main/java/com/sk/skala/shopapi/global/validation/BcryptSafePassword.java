package com.sk.skala.shopapi.global.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * BCrypt가 온전히 반영할 수 있는 길이인지 검사한다.
 *
 * <p><b>{@code @Size}로는 이 문제를 막을 수 없다.</b> {@code @Size}는 문자 개수를 세지만
 * BCrypt는 <b>바이트 수</b>를 기준으로 72바이트까지만 반영하고 나머지를 조용히 버린다.
 * UTF-8에서 한글 한 글자는 3바이트이므로 {@code @Size(max = 64)}를 통과한 한글 64자는
 * 192바이트가 되어 앞의 24자만 실제 비밀번호가 된다.
 *
 * <p>그러면 앞 24자가 같은 서로 다른 비밀번호가 <b>같은 해시로 인증된다.</b>
 * 사용자는 긴 비밀번호를 썼다고 믿지만 실제 강도는 24자에 머문다.
 *
 * <p>예외를 던지지 않고 조용히 잘라내기 때문에 테스트로 잡지 않으면 드러나지 않는다.
 * 그래서 입력 단계에서 막는다.
 */
@Documented
@Constraint(validatedBy = BcryptSafePasswordValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface BcryptSafePassword {

    /**
     * 검증 실패 시 사용자에게 보여줄 메시지.
     *
     * <p>"64자 이하"가 아니라 "72바이트"라고 쓴다. 문자 수로 안내하면 한글 사용자는
     * 왜 24자에서 막히는지 알 수 없다. 기준이 바이트임을 드러내야 원인을 짐작할 수 있다.
     */
    String message() default "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다";

    /**
     * 검증 그룹.
     *
     * <p>Bean Validation 명세가 모든 제약에 요구하는 멤버라 비워 두더라도 선언해야 한다.
     * 없으면 제약 애노테이션으로 인식되지 않는다.
     */
    Class<?>[] groups() default {};

    /**
     * 검증 실패에 부가 정보를 실어 보내기 위한 확장 지점.
     *
     * <p>{@code groups}와 마찬가지로 명세가 요구하는 필수 멤버다. 이 프로젝트에서는 쓰지 않는다.
     */
    Class<? extends Payload>[] payload() default {};
}
