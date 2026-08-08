package com.sk.skala.shopapi.global.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.sk.skala.shopapi.customer.domain.Role;

import lombok.RequiredArgsConstructor;

/**
 * 인증·인가 규칙.
 *
 * <p>규칙을 컨트롤러마다 흩뿌리지 않고 여기 한곳에 모은다. 그래야 "무엇이 열려 있고
 * 무엇이 막혀 있는가"를 한 화면에서 확인할 수 있다. 흩어져 있으면 새 엔드포인트를 추가할 때
 * 인가를 빠뜨려도 아무도 모른다.
 *
 * <h2>P1에서 미뤄둔 인가를 여기서 닫는다</h2>
 *
 * <p>P1은 "엔드포인트를 정의"만 하고 인가는 P2로 미뤘다. 그때 열려 있던 것들이다.
 *
 * <pre>
 *   DELETE /api/customers/{id}          아무 계정이나 삭제 가능했음
 *   PUT    /api/customers/{id}          아무 계정의 잔액 설정 가능했음
 *   POST   /api/customers/{id}/points   아무 계정에나 충전 가능했음
 *   GET    /api/customers               전체 고객 목록·잔액 조회 가능했음
 *   POST PUT DELETE /api/products       아무나 상품 관리 가능했음
 * </pre>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final SecurityProblemHandlers problemHandlers;

    /**
     * H2 콘솔을 열어둘지 판단하는 데 쓴다.
     *
     * <p>{@code local} 프로파일에서만 연다. 프로파일과 무관하게 열어두면
     * <b>운영에 그대로 나가 누구나 브라우저로 DB 전체를 조회하고 수정할 수 있다.</b>
     * 인증도 없다. prod는 PostgreSQL이라 콘솔이 뜨지 않지만, 그건 우연한 안전이지
     * 설계된 안전이 아니다. 설정 하나만 바뀌면 무너진다.
     */
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean localProfile = environment.matchesProfiles("local");

        http
                // D18: 액세스 토큰을 쿠키가 아니라 Authorization 헤더로 받는다.
                // 브라우저가 자동으로 붙이지 않으므로 CSRF 공격이 성립하지 않는다.
                // 리프레시 쿠키는 Path를 /api/auth/refresh로 제한해 다른 경로로 전송되지 않는다.
                .csrf(csrf -> csrf.disable())

                // JWT는 그 자체로 상태를 담으므로 서버 세션이 필요 없다.
                // STATELESS로 두면 스프링이 세션을 만들지 않아 수평 확장 시 세션 공유 문제가 없다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 기본 로그인 폼과 브라우저 인증창을 끈다. REST API에는 맞지 않는다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(problemHandlers.accessDeniedHandler()))

                .authorizeHttpRequests(auth -> {
                    auth
                        // ── 공개 ──
                        // 회원가입과 로그인은 인증 전에 호출되므로 열어야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        // 명세 536p의 로그인 경로. 인증 전에 호출되므로 열어야 한다. (D27)
                        .requestMatchers(HttpMethod.POST, "/api/customers/login").permitAll()
                        // 상품 조회는 비로그인 방문자도 볼 수 있어야 쇼핑몰이 성립한다.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*",
                                "/api/products/categories").permitAll()

                        // 선착순 이벤트 조회는 공개다. 비로그인 방문자가 어떤 이벤트가
                        // 열리는지 볼 수 없으면 참여할 마음이 생기지 않는다. (D23)
                        .requestMatchers(HttpMethod.GET, "/api/flash-sales", "/api/flash-sales/*").permitAll()
                        // 문서와 헬스체크. 운영 도구가 인증 없이 접근한다.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 쿠버네티스 probe는 인증 없이 호출된다. kubelet은 토큰이 없다.
                        // 하위 경로까지 여는 이유는 probes.enabled로 health/liveness와
                        // health/readiness가 생기기 때문이다. (D24)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/info").permitAll()

                        // 모의 카드사. 실제로는 다른 회사의 다른 서버라 우리 인증 체계가
                        // 적용될 자리가 아니다. 막아두면 우리 서버가 자기 자신을 부를 때 401을 받는다.
                        // 실제 배포에서는 이 컨트롤러를 아예 뺀다. (D32)
                        .requestMatchers("/mock-issuer/**").permitAll()

                        // ── 관리자 전용 ──
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/customers").hasRole(Role.ADMIN.name())
                        // 잔액을 임의 값으로 덮어쓰는 동작이라 본인에게도 열지 않는다. (D13)
                        .requestMatchers(HttpMethod.PUT, "/api/customers/*").hasRole(Role.ADMIN.name())

                        // 명세 536p 경로에는 경로 변수가 없어 위 패턴에 걸리지 않는다.
                        // 따로 적지 않으면 anyRequest로 흘러가 로그인한 일반 고객이
                        // 상품을 고치고 지울 수 있다. (D27)
                        .requestMatchers(HttpMethod.PUT, "/api/products").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/products").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/customers").hasRole(Role.ADMIN.name())

                        // 메트릭은 관리자만 본다. 열어두면 엔드포인트 목록과 호출 빈도가
                        // 드러나 공격 대상을 고르는 단서가 된다. (D28)
                        .requestMatchers("/actuator/**").hasRole(Role.ADMIN.name())

                        ;

                    // ── H2 콘솔: local 프로파일에서만 ──
                    //
                    // 콘솔에는 인증이 없다. 열려 있으면 누구나 브라우저로 DB를 통째로
                    // 조회하고 수정할 수 있다. prod는 PostgreSQL이라 콘솔이 뜨지 않지만,
                    // 그건 우연한 안전이지 설계된 안전이 아니다. 설정 하나만 바뀌면 무너진다.
                    //
                    // anyRequest보다 먼저 선언해야 한다. 뒤에 두면
                    // "Can't configure mvcMatchers after anyRequest"로 기동이 실패한다.
                    if (localProfile) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    // ── 로그인 필요 ──
                    // 본인 여부는 여기서 못 가린다. 경로 변수와 토큰 주체를 비교해야 하므로
                    // 서비스 계층에서 확인한다. 필터 체인은 "로그인했는가"까지만 책임진다.
                    auth.anyRequest().authenticated();
                })

                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);

        // H2 콘솔은 프레임으로 렌더링되어 기본 X-Frame-Options에 막힌다.
        // 그 완화도 필요한 곳에서만 한다. 전역으로 풀면 클릭재킹 방어가 사라진다.
        if (localProfile) {
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }
}
