package com.sk.skala.shopapi.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * {@link GlobalExceptionHandler} 통합 테스트.
 *
 * <p>확인하려는 것은 두 가지다.
 *
 * <p>첫째, 우리가 정의한 업무 예외가 의도한 상태 코드와 형식으로 나가는가.
 *
 * <p>둘째, <b>스프링이 만들어내는 프레임워크 예외의 상태 코드가 보존되는가.</b>
 * {@code @ExceptionHandler(Exception.class)} 하나만 두면 405·415·400 같은 예외까지
 * 전부 삼켜서 500으로 바꿔버린다. {@code ExceptionHandlerExceptionResolver}가
 * {@code DefaultHandlerExceptionResolver}보다 먼저 실행되기 때문이다.
 * 클라이언트 입장에서는 자기가 잘못 보낸 요청이 서버 장애로 보이고,
 * 모니터링에서는 5xx 알람이 잘못 울린다.
 *
 * <p>이 테스트가 그 회귀를 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 예외 상황을 만들기 위한 테스트 전용 컨트롤러. 운영 코드에는 포함되지 않는다.
     *
     * <p>{@code @RestController}가 붙은 중첩 클래스는 설정 클래스가 스캔될 때 자동으로 빈이 된다.
     * 여기에 {@code @Bean} 메서드까지 두면 같은 핸들러가 두 번 등록돼
     * "Ambiguous mapping"으로 컨텍스트 로딩이 실패한다.
     */
    @TestConfiguration
    static class TestControllerConfig {

        @RestController
        static class TestController {

            record SampleRequest(@NotBlank(message = "이름은 필수입니다") String name) {
            }

            @PostMapping("/test/business-error")
            void businessError() {
                throw new BusinessException(ErrorCode.INSUFFICIENT_POINT, "필요 30000원, 보유 12000원");
            }

            @PostMapping("/test/validate")
            void validate(@Valid @RequestBody SampleRequest request) {
            }
        }
    }

    @Nested
    @DisplayName("업무 예외")
    class BusinessErrors {

        @Test
        @DisplayName("BusinessException은 코드에 지정된 상태와 형식으로 응답한다")
        void businessException() throws Exception {
            mockMvc.perform(post("/test/business-error"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"))
                    .andExpect(jsonPath("$.title").value("포인트가 부족합니다"))
                    .andExpect(jsonPath("$.detail").value("필요 30000원, 보유 12000원"))
                    .andExpect(jsonPath("$.type").value("https://skala-shop/errors/insufficient-point"));
        }

        @Test
        @DisplayName("검증 실패는 400과 함께 어떤 필드가 틀렸는지 알려준다")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/test/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.errors.name").value("이름은 필수입니다"));
        }
    }

    @Nested
    @DisplayName("프레임워크 예외 - 상태 코드가 500으로 뭉개지면 안 된다")
    class FrameworkErrors {

        @Test
        @DisplayName("본문 JSON이 깨졌으면 400이다")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/test/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ 깨진 JSON "))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("지원하지 않는 HTTP 메서드는 405다")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(get("/test/business-error"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("지원하지 않는 Content-Type은 415다")
        void unsupportedMediaType() throws Exception {
            mockMvc.perform(post("/test/validate")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("name=skala"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("없는 경로는 404다")
        void notFound() throws Exception {
            mockMvc.perform(get("/test/does-not-exist"))
                    .andExpect(status().isNotFound());
        }
    }
}
