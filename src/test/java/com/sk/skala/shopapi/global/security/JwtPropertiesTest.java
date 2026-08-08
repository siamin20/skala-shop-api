package com.sk.skala.shopapi.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JWT 설정 검증.
 *
 * <p>확인하려는 것은 하나다. <b>잘못된 시크릿으로는 애플리케이션이 뜨지 않는가.</b>
 *
 * <p>예전에는 {@code application.yml}에 로컬 개발용 시크릿이 박혀 있었다.
 * 이 저장소는 공개되어 있어 누구나 그 값을 읽을 수 있고, 그대로 배포되면
 * <b>아무나 {@code role=ADMIN} 토큰을 만들어 서명까지 맞출 수 있다.</b>
 *
 * <p>주석으로 "운영에서 바꾸세요"라고 적어두는 것으로는 부족하다. 잊으면 그만이기 때문이다.
 * 기동 자체를 막아야 잊을 수 없다.
 */
@DisplayName("JWT 설정 검증")
class JwtPropertiesTest {

    private static final Duration ACCESS = Duration.ofMinutes(15);
    private static final Duration REFRESH = Duration.ofDays(14);

    private JwtProperties create(String secret) {
        return new JwtProperties(secret, ACCESS, REFRESH, null);
    }

    @Test
    @DisplayName("시크릿이 없으면 기동을 막는다")
    void rejectMissingSecret() {
        assertThatThrownBy(() -> create(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("시크릿이 공백뿐이어도 막는다")
    void rejectBlankSecret() {
        // 빈 문자열만 검사하면 "   "가 통과한다. 그 값으로 만든 키는 예측 가능하다.
        assertThatThrownBy(() -> create("        "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("32바이트 미만이면 막는다")
    void rejectShortSecret() {
        assertThatThrownBy(() -> create("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("길이를 글자 수가 아니라 바이트 수로 센다")
    void countsBytesNotCharacters() {
        // 한글 11자는 33바이트다. 글자 수로 셌다면 11 < 32라 거부됐을 것이다.
        // 반대로 짧은 한글은 글자 수로는 통과해도 바이트로는 부족할 수 있다.
        // BCrypt 72바이트 절단(D14)에서 겪은 것과 같은 함정이다.
        String elevenKoreanChars = "가나다라마바사아자차카";
        assertThat(elevenKoreanChars).hasSize(11);

        assertThatCode(() -> create(elevenKoreanChars)).doesNotThrowAnyException();

        // 10자는 30바이트라 부족하다
        assertThatThrownBy(() -> create("가나다라마바사아자차"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cookieSecure를 지정하지 않으면 안전한 쪽인 true가 된다")
    void cookieSecureDefaultsToTrue() {
        // 설정을 빠뜨렸을 때 기울어지는 방향이 중요하다.
        // primitive boolean이었다면 누락 시 false가 되어 평문 HTTP로도
        // 리프레시 쿠키가 나갔을 것이다.
        assertThat(create("a-secret-that-is-long-enough-for-hs256").cookieSecure()).isTrue();
    }
}
