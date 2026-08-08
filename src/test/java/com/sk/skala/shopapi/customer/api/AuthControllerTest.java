package com.sk.skala.shopapi.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.customer.domain.Role;
import com.sk.skala.shopapi.global.common.Money;

import jakarta.servlet.http.Cookie;

/**
 * 인증 흐름 통합 테스트.
 *
 * <p>다른 테스트가 {@code @WithMockUser}로 인증을 흉내 내는 것과 달리, 여기서는
 * <b>실제로 로그인해 토큰을 받고 그 토큰으로 보호된 API를 호출</b>한다.
 *
 * <p>흉내만으로는 JWT 발급·서명·파싱·필터 연결이 전혀 검증되지 않는다.
 * 필터가 통째로 잘못돼도 다른 테스트는 모두 통과한다. 그 구멍을 이 클래스가 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "pw123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAllInBatch();
        customerRepository.save(new Customer(
                "skala01", passwordEncoder.encode(PASSWORD), Money.of(1_000_000)));
        customerRepository.save(new Customer(
                "admin01", passwordEncoder.encode(PASSWORD), Money.ZERO, Role.ADMIN));
    }

    /** 로그인 요청 본문. */
    private String credentials(String customerId, String password) {
        return "{\"customerId\":\"%s\",\"password\":\"%s\"}".formatted(customerId, password);
    }

    /** 로그인해 액세스 토큰을 얻는다. */
    private String login(String customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(customerId, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("로그인하면 액세스 토큰은 본문으로, 리프레시 토큰은 HttpOnly 쿠키로 나온다")
    void loginIssuesBothTokens() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("skala01"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                // 리프레시 토큰이 본문에 있으면 JS가 읽을 수 있어 HttpOnly가 무의미해진다.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists("bff-refresh"))
                .andExpect(cookie().httpOnly("bff-refresh", true))
                // 이 경로 외에는 쿠키가 전송되지 않아 노출 횟수가 줄어든다.
                .andExpect(cookie().path("bff-refresh", "/api/auth/refresh"));
    }

    @Test
    @DisplayName("발급받은 토큰으로 보호된 API를 호출할 수 있다")
    void accessTokenGrantsAccess() throws Exception {
        String token = login("skala01");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("skala01"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("아이디가 없어도 비밀번호가 틀려도 같은 응답을 준다")
    void doesNotRevealAccountExistence() throws Exception {
        // 구분해서 알려주면 공격자가 "이 아이디는 존재한다"를 알아내 대상을 좁힐 수 있다. (D19)
        String 없는아이디 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String 틀린비밀번호 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", "wrongpassword")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(없는아이디).isEqualTo(틀린비밀번호);
    }

    @Test
    @DisplayName("리프레시 쿠키로 새 액세스 토큰을 받는다")
    void refreshIssuesNewAccessToken() throws Exception {
        Cookie refreshCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andReturn().getResponse().getCookie("bff-refresh");

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("skala01"));
    }

    @Test
    @DisplayName("리프레시 토큰으로는 일반 API를 호출할 수 없다")
    void refreshTokenCannotAccessApi() throws Exception {
        String refreshToken = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andReturn().getResponse().getCookie("bff-refresh").getValue();

        // 종류를 검사하지 않으면 수명이 긴 리프레시 토큰으로 API를 호출할 수 있어
        // 액세스 토큰을 15분으로 짧게 둔 의미가 사라진다.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리프레시 쿠키 없이 갱신하면 401")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    @DisplayName("로그아웃하면 리프레시 쿠키가 만료된다")
    void logoutExpiresCookie() throws Exception {
        String token = login("skala01");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("bff-refresh", 0));
    }

    @Test
    @DisplayName("로그아웃하면 이미 발급된 리프레시 토큰도 무효가 된다")
    void logoutInvalidatesIssuedRefreshTokens() throws Exception {
        // 공격자가 리프레시 토큰을 확보한 상황을 흉내 낸다.
        // 쿠키를 손에 넣었으므로 브라우저에서 쿠키가 지워져도 이 값은 그대로 남는다.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andReturn();

        Cookie stolen = loginResult.getResponse().getCookie("bff-refresh");
        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // 훔친 토큰은 로그아웃 전에는 잘 동작한다
        mockMvc.perform(post("/api/auth/refresh").cookie(stolen))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 쿠키만 만료시켰다면 훔친 토큰은 그대로 통과한다.
        // 토큰 버전을 올려야 여기서 막힌다. 막히지 않으면 로그아웃해도
        // 공격자가 최대 14일 동안 액세스 토큰을 계속 재발급받는다. (D25)
        mockMvc.perform(post("/api/auth/refresh").cookie(stolen))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 후 다시 로그인하면 새 리프레시 토큰은 동작한다")
    void loginAfterLogoutWorks() throws Exception {
        String token = login("skala01");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token));

        // 무효화가 과하게 걸려 재로그인까지 막으면 아무도 다시 못 들어온다.
        Cookie fresh = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andReturn().getResponse().getCookie("bff-refresh");

        mockMvc.perform(post("/api/auth/refresh").cookie(fresh))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("한 기기에서 로그아웃하면 다른 기기의 리프레시 토큰도 끊긴다")
    void logoutInvalidatesAllDevices() throws Exception {
        Cookie phone = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("skala01", PASSWORD)))
                .andReturn().getResponse().getCookie("bff-refresh");

        String laptopToken = login("skala01");

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + laptopToken));

        // 의도한 동작이다. 기기별로 끊으려면 세션 식별자가 따로 필요한데
        // 그건 무상태 JWT의 범위를 벗어난다. 이 테스트는 그 한계를 고정해둔다.
        mockMvc.perform(post("/api/auth/refresh").cookie(phone))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자로 로그인하면 역할이 ADMIN이다")
    void adminLogin() throws Exception {
        String token = login("admin01");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("페이로드를 변조한 토큰은 거부한다")
    void rejectTamperedPayload() throws Exception {
        String token = login("skala01");
        String[] parts = token.split("\\.");

        // 페이로드(2번째 조각)의 첫 글자를 바꾼다. 서명은 헤더+페이로드로 계산되므로
        // 페이로드가 달라지면 서명 검증이 실패한다.
        //
        // 처음에는 토큰 맨 끝 글자를 바꿨는데 검증을 통과했다.
        // HS256 서명은 32바이트 = base64url 43글자인데 43×6=258비트라
        // 마지막 글자의 하위 2비트는 디코딩 시 버려진다. 그 비트만 바꾸면 서명 바이트가 그대로다.
        char first = parts[1].charAt(0);
        parts[1] = (first == 'e' ? 'f' : 'e') + parts[1].substring(1);
        String tampered = String.join(".", parts);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("서명을 다른 값으로 바꾼 토큰은 거부한다")
    void rejectTamperedSignature() throws Exception {
        String token = login("skala01");
        String[] parts = token.split("\\.");

        // 서명 조각을 통째로 다른 값으로 바꾼다
        parts[2] = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String tampered = String.join(".", parts);


        // 페이로드(2번째 조각)의 첫 글자를 바꾼다. 서명은 헤더+페이로드로 계산되므로
        // 페이로드가 달라지면 서명 검증이 실패한다.
        //
        // 처음에는 토큰 맨 끝 글자를 바꿨다. 그런데 HS256 서명은 32바이트이고
        // base64url로 43글자인데 43×6 = 258비트다. 32바이트는 256비트이므로
        // 마지막 글자의 하위 2비트는 디코딩할 때 버려진다.
        // 그 비트만 건드리면 디코딩된 서명 바이트가 그대로라 검증을 통과한다.
        // 즉 이 테스트는 아무것도 검증하지 못하고 있었다.
        char first = parts[1].charAt(0);
        parts[1] = (first == 'e' ? 'f' : 'e') + parts[1].substring(1);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }
}
