package com.sk.skala.shopapi.customer.dto;

import com.sk.skala.shopapi.global.validation.BcryptSafePassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 *
 * <p>과제 명세 552p의 {@code createCustomer}에 대응한다.
 * 명세는 {@code Customer} 엔티티를 그대로 받지만 요청 DTO로 분리했다(D3).
 * 엔티티를 받으면 클라이언트가 {@code customerPoint}를 직접 보내
 * <b>원하는 만큼 포인트를 갖고 가입할 수 있다.</b> 초기 포인트는 서버가 정할 값이므로
 * 요청 DTO에 아예 두지 않는다.
 *
 * <p>비밀번호는 여기까지만 평문으로 존재한다. 서비스에서 즉시 해시로 바꾸며,
 * 엔티티에는 해시만 저장된다(D5).
 *
 * @param customerId 로그인 아이디
 * @param password   평문 비밀번호
 */
public record SignUpRequest(

        // 아이디를 경로 변수로도 쓰므로 URL에서 문제를 일으키는 문자를 미리 막는다.
        // 슬래시나 물음표가 들어가면 /api/customers/{customerId} 경로가 깨진다.
        @NotBlank(message = "아이디는 필수입니다")
        @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "아이디는 영문, 숫자, -, _만 사용할 수 있습니다")
        String customerId,

        // 하한 8자는 최소한의 무차별 대입 저항을 위한 것이다.
        // 상한은 @Size로 걸 수 없다. @Size는 문자 수를 세는데 BCrypt는 바이트 수로 자르기 때문에,
        // 한글처럼 여러 바이트를 쓰는 문자에서 검사가 새어 나간다. @BcryptSafePassword 참고.
        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        @BcryptSafePassword
        String password) {
}
