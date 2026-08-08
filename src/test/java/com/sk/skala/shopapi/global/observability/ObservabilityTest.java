package com.sk.skala.shopapi.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import com.sk.skala.shopapi.customer.domain.Role;
import com.sk.skala.shopapi.support.WithMockCustomer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 관측 장치 검증. (D28)
 *
 * <p>관측은 <b>있는지 없는지가 조용하다.</b> 로그가 안 찍혀도 기능은 돌고,
 * 메트릭이 비어 있어도 아무도 실패하지 않는다. 그래서 테스트로 고정해둔다.
 * 문제가 터진 뒤에 "로그가 없네"를 발견하면 그때는 늦다.
 */
@SpringBootTest
@AutoConfigureMockMvc
// 스프링 부트 테스트는 기본으로 관측 기능을 끈다. 테스트마다 메트릭 레지스트리를
// 만드는 비용을 아끼려는 것인데, 그 결과 /actuator/prometheus가 404가 된다.
// 관측 자체를 검증하는 이 클래스에서는 다시 켜야 한다. (D28)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@DisplayName("관측")
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Nested
    @DisplayName("추적 아이디")
    class TraceId {

        @Test
        @DisplayName("모든 응답에 X-Trace-Id가 실린다")
        @WithAnonymousUser
        void responseCarriesTraceId() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Trace-Id"));
        }

        @Test
        @DisplayName("인증에 실패한 요청에도 추적 아이디가 붙는다")
        @WithAnonymousUser
        void unauthorizedAlsoCarriesTraceId() throws Exception {
            // 필터를 인증 필터보다 앞에 두지 않으면 401 로그에 아이디가 없다.
            // 401이 왜 났는지 쫓아야 할 때 정작 그 로그를 못 찾는다.
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("X-Trace-Id"));
        }

        @Test
        @DisplayName("클라이언트가 보낸 추적 아이디를 이어받는다")
        @WithAnonymousUser
        void inheritsClientTraceId() throws Exception {
            // 새로 만들면 같은 사용자 동작이 서비스 경계마다 다른 아이디로 쪼개진다.
            mockMvc.perform(get("/api/products").header("X-Trace-Id", "front-abc123"))
                    .andExpect(header().string("X-Trace-Id", "front-abc123"));
        }

        @Test
        @DisplayName("위험한 문자가 섞인 추적 아이디는 걸러낸다")
        @WithAnonymousUser
        void sanitizesClientTraceId() throws Exception {
            // 줄바꿈을 그대로 받으면 로그에 가짜 줄을 심을 수 있다(로그 위조).
            mockMvc.perform(get("/api/products").header("X-Trace-Id", "bad\nINFO fake-log"))
                    .andExpect(header().string("X-Trace-Id", "badINFOfake-log"));
        }
    }

    @Nested
    @DisplayName("메트릭")
    class Metrics {

        @Test
        @DisplayName("API 호출이 타이머에 기록된다")
        @WithAnonymousUser
        void apiCallIsTimed() throws Exception {
            mockMvc.perform(get("/api/products")).andExpect(status().isOk());

            assertThat(meterRegistry.find("shop.api.duration")
                    .tag("endpoint", "ProductController.getProducts")
                    .timer())
                    .as("AOP가 컨트롤러 호출을 계측해야 한다")
                    .isNotNull();
        }

        @Test
        @DisplayName("락 관련 카운터가 등록되어 있다")
        void lockCountersRegistered() {
            // 동시성 지표는 문제가 터지기 전에 미리 보이는 것이 중요하다.
            // 충돌이 늘어나는 추세를 보면 재시도 상한을 조정할 시점을 알 수 있다.
            for (String name : new String[] {
                    "shop.lock.optimistic.conflicts",
                    "shop.lock.retry.exhausted",
                    "shop.lock.pessimistic.timeouts"}) {
                assertThat(meterRegistry.find(name).counter())
                        .as("%s 카운터가 등록되어야 한다", name)
                        .isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Actuator 노출 범위")
    class ActuatorExposure {

        @Test
        @DisplayName("health는 인증 없이 열려 있다")
        @WithAnonymousUser
        void healthIsPublic() throws Exception {
            // 쿠버네티스 probe는 토큰을 갖고 있지 않다. 막으면 파드가 Ready가 되지 않는다. (D24)
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("메트릭은 비로그인에게 닫혀 있다")
        @WithAnonymousUser
        void metricsRequireAuth() throws Exception {
            // 열어두면 엔드포인트 목록과 호출 빈도가 그대로 드러나
            // 공격 대상을 고르는 단서가 된다.
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("일반 고객도 메트릭을 볼 수 없다")
        @WithMockCustomer("skala01")
        void customerCannotSeeMetrics() throws Exception {
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("관리자는 Prometheus 형식으로 메트릭을 받는다")
        @WithMockCustomer(value = "admin01", role = Role.ADMIN)
        void adminSeesPrometheus() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("shop_lock_optimistic_conflicts")));
        }
    }
}
