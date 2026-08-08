package com.sk.skala.shopapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱 설정.
 *
 * <p>D5: 명세는 비밀번호 암호화를 언급하지 않지만 평문 저장은 하지 않는다.
 * DB가 유출되면 평문은 그 즉시 모든 계정이 뚫린다.
 *
 * <p>BCrypt를 쓰는 이유는 <b>의도적으로 느리기</b> 때문이다. SHA-256 같은 범용 해시는
 * 빠른 것이 장점이라, 공격자가 초당 수십억 번 대입할 수 있어 비밀번호 저장에는 오히려 불리하다.
 * BCrypt는 연산 강도를 조절할 수 있어 하드웨어가 빨라져도 강도만 올리면 된다.
 * 또 해시마다 무작위 솔트를 자동으로 섞어 저장하므로, 같은 비밀번호도 매번 다른 값이 되고
 * 미리 계산된 표(레인보우 테이블)로 뚫을 수 없다.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 인코더를 빈으로 등록한다.
     *
     * <p>강도를 지정하지 않으면 기본값 10이 쓰인다. 해시 한 번에 수십 밀리초가 걸리는데,
     * 로그인은 자주 일어나는 요청이 아니므로 감수할 만하고 무차별 대입에는 큰 벽이 된다.
     *
     * <p>반환 타입을 구현 클래스가 아니라 {@link PasswordEncoder} 인터페이스로 둔다.
     * 나중에 알고리즘을 바꿔도 쓰는 쪽 코드를 고치지 않는다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
