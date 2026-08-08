package com.sk.skala.shopapi.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인가 규칙 검증.
 *
 * <p>P1에서 "P2에서 닫는다"고 미뤄둔 엔드포인트들이 실제로 닫혔는지 확인한다.
 * 각 PR 본문과 `docs/03-api.md`에 표로 적어둔 약속이 코드로 지켜지는지가 이 테스트의 목적이다.
 *
 * <p>세 가지를 구분해서 본다.
 *
 * <ul>
 *   <li><b>401</b> — 로그인하지 않았다. 누구인지 모른다
 *   <li><b>403</b> — 로그인은 했지만 권한이 없다
 *   <li><b>통과</b> — 공개 엔드포인트이거나 권한이 있다
 * </ul>
 *
 * <p>401과 403을 구분하는 것이 중요하다. 둘을 같게 두면 클라이언트가
 * "다시 로그인하라"와 "이 계정으로는 안 된다"를 구별해 안내할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRuleTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("공개 - 로그인 없이 접근할 수 있어야 한다")
    @WithAnonymousUser
    class PublicEndpoints {

        @Test
        @DisplayName("상품 목록 조회")
        void productList() throws Exception {
            // 비로그인 방문자가 상품을 볼 수 없으면 쇼핑몰이 성립하지 않는다.
            mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("상품 상세 조회")
        void productDetail() throws Exception {
            mockMvc.perform(get("/api/products/1")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("회원가입")
        void signUp() throws Exception {
            // 인증 전에 호출되므로 열려 있어야 한다. 막으면 아무도 가입할 수 없다.
            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"newbie01\",\"password\":\"pw123456\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Swagger 문서")
        void swagger() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("비로그인 - 401을 받아야 한다")
    @WithAnonymousUser
    class RequiresAuthentication {

        @Test
        @DisplayName("주문 조회")
        void orders() throws Exception {
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        }

        @Test
        @DisplayName("고객 상세 조회")
        void customerDetail() throws Exception {
            mockMvc.perform(get("/api/customers/skala01"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("포인트 충전")
        void chargePoint() throws Exception {
            mockMvc.perform(post("/api/customers/skala01/points")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":5000}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("상품 등록")
        void createProduct() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"침입자상품\",\"price\":1000}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 응답도 ProblemDetail 형식이다")
        void unauthorizedIsProblemDetail() throws Exception {
            // 시큐리티 필터는 컨트롤러 전에 거부하므로 @RestControllerAdvice가 잡지 못한다.
            // 따로 처리하지 않으면 401만 형식이 달라져 클라이언트가 두 규약을 다뤄야 한다.
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                    .andExpect(jsonPath("$.title").exists())
                    .andExpect(jsonPath("$.type").value("https://skala-shop/errors/not-authenticated"));
        }
    }

    @Nested
    @DisplayName("일반 고객 - 관리자 전용에는 403을 받아야 한다")
    @WithMockUser(username = "skala01", roles = "CUSTOMER")
    class CustomerCannotActAsAdmin {

        @Test
        @DisplayName("상품 등록")
        void createProduct() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"침입자상품\",\"price\":1000}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("상품 삭제")
        void deleteProduct() throws Exception {
            mockMvc.perform(delete("/api/products/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("전체 고객 목록 조회")
        void customerList() throws Exception {
            // 막지 않으면 모든 고객의 아이디와 잔액이 노출된다.
            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("포인트 조정 - 잔액을 임의 값으로 덮어쓰는 동작이라 본인에게도 막는다")
        void adjustPoint() throws Exception {
            // 열어두면 누구나 자기 잔액을 원하는 값으로 설정할 수 있다. (D13)
            mockMvc.perform(put("/api/customers/skala01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"point\":99999999}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("관리자 - 관리 기능에 접근할 수 있다")
    @WithMockUser(username = "admin01", roles = "ADMIN")
    class AdminCanManage {

        @Test
        @DisplayName("상품 등록")
        void createProduct() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"관리자등록상품\",\"price\":1000}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("전체 고객 목록 조회")
        void customerList() throws Exception {
            mockMvc.perform(get("/api/customers")).andExpect(status().isOk());
        }
    }
}
