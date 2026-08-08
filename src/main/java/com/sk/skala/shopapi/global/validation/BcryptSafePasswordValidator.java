package com.sk.skala.shopapi.global.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link BcryptSafePassword}의 검사 로직.
 *
 * <p>BCrypt 명세상 입력은 최대 72바이트까지만 유효하다. Spring Security의 구현도
 * 그 이상을 잘라낼 뿐 예외를 던지지 않으므로, 여기서 걸러야 사용자가 알 수 있다.
 */
public class BcryptSafePasswordValidator implements ConstraintValidator<BcryptSafePassword, String> {

    /** BCrypt가 반영하는 최대 입력 길이(바이트). 알고리즘이 정한 값이라 조정 대상이 아니다. */
    private static final int BCRYPT_MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 여부는 @NotBlank가 판단한다. 여기서 false를 주면 "필수" 대신
        // "72바이트 초과" 메시지가 나가 사용자가 원인을 오해한다.
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
