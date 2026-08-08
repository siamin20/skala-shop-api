package com.sk.skala.shopapi.product.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.product.domain.Product;
import com.sk.skala.shopapi.product.domain.ProductRepository;

/**
 * 상품 API 통합 테스트.
 *
 * <p>서비스 테스트가 업무 규칙을 확인한다면, 여기서는 <b>HTTP 경계</b>를 확인한다.
 * 상태 코드, 응답 형식, 검증 실패 시 나가는 오류 본문이 대상이다.
 * 서비스 테스트만으로는 "규칙은 맞는데 404여야 할 것이 500으로 나가는" 문제를 잡지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // deleteAllInBatch()를 쓰는 이유는 ProductServiceTest 주석 참고.
        // deleteAll()은 DELETE가 flush까지 지연돼 시드와 유니크 제약 충돌이 난다.
        productRepository.deleteAllInBatch();
    }

    private Product 저장된상품(String name, long price) {
        return productRepository.save(new Product(name, Money.of(price)));
    }

    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        @DisplayName("GET /api/products - 페이지 응답 형식을 지킨다")
        void getProducts() throws Exception {
            저장된상품("무선마우스", 15_000);
            저장된상품("USB허브", 39_000);

            mockMvc.perform(get("/api/products").param("page", "0").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.last").value(false));
        }

        @Test
        @DisplayName("페이지 크기 상한을 넘으면 400")
        void rejectOversizedPage() throws Exception {
            // 상한이 없으면 ?size=1000000 한 번으로 전체 테이블을 메모리에 올릴 수 있다.
            // @Validated가 빠지면 이 검증이 조용히 무시되므로 이 테스트가 그것도 함께 막는다.
            mockMvc.perform(get("/api/products").param("size", "1000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.errors.size").exists());
        }

        @Test
        @DisplayName("음수 페이지 번호는 400")
        void rejectNegativePage() throws Exception {
            mockMvc.perform(get("/api/products").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.page").exists());
        }

        @Test
        @DisplayName("GET /api/products/{id} - 상세를 조회한다")
        void getProduct() throws Exception {
            Product saved = 저장된상품("무선마우스", 15_000);

            mockMvc.perform(get("/api/products/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(saved.getId()))
                    .andExpect(jsonPath("$.name").value("무선마우스"))
                    .andExpect(jsonPath("$.price").value(15_000));
        }

        @Test
        @DisplayName("없는 상품은 404와 ProblemDetail")
        void notFound() throws Exception {
            mockMvc.perform(get("/api/products/{id}", 9999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"))
                    .andExpect(jsonPath("$.type").value("https://skala-shop/errors/data-not-found"));
        }

        @Test
        @DisplayName("ID가 숫자가 아니면 400")
        void rejectNonNumericId() throws Exception {
            mockMvc.perform(get("/api/products/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * 상품 등록. P2부터 관리자 전용이다.
     *
     * <p>클래스에 {@code @WithMockUser(roles = "ADMIN")}를 걸어 인가를 통과시킨다.
     * 권한이 없을 때 403이 나가는지는 {@code SecurityRuleTest}가 따로 확인한다.
     * 여기서 둘을 섞으면 "검증 실패로 400"인지 "권한 없어 403"인지 구분이 흐려진다.
     */
    @Nested
    @DisplayName("등록")
    @WithMockUser(roles = "ADMIN")
    class Create {

        @Test
        @DisplayName("POST /api/products - 201과 Location 헤더를 준다")
        void createProduct() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"무선마우스\",\"price\":15000}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.name").value("무선마우스"))
                    .andExpect(jsonPath("$.price").value(15_000));
        }

        @Test
        @DisplayName("상품명이 비면 400과 필드별 사유")
        void rejectBlankName() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"  \",\"price\":15000}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.errors.name").value("상품명은 필수입니다"));
        }

        @Test
        @DisplayName("가격이 0이면 400")
        void rejectZeroPrice() throws Exception {
            // 명세의 "가격 0이면 오류" 규칙. @Positive가 0도 거부한다.
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"무선마우스\",\"price\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.price").value("가격은 0보다 커야 합니다"));
        }

        @Test
        @DisplayName("이름이 중복이면 409")
        void rejectDuplicate() throws Exception {
            저장된상품("무선마우스", 15_000);

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"무선마우스\",\"price\":20000}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DATA_DUPLICATED"));
        }
    }

    /** 상품 수정·삭제. 등록과 같은 이유로 관리자 권한이 필요하다. */
    @Nested
    @DisplayName("수정과 삭제")
    @WithMockUser(roles = "ADMIN")
    class UpdateAndDelete {

        @Test
        @DisplayName("PUT /api/products/{id} - 수정한다")
        void updateProduct() throws Exception {
            Product saved = 저장된상품("무선마우스", 15_000);

            mockMvc.perform(put("/api/products/{id}", saved.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"무선마우스 v2\",\"price\":18000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("무선마우스 v2"))
                    .andExpect(jsonPath("$.price").value(18_000));
        }

        @Test
        @DisplayName("DELETE /api/products/{id} - 204를 준다")
        void deleteProduct() throws Exception {
            Product saved = 저장된상품("무선마우스", 15_000);

            mockMvc.perform(delete("/api/products/{id}", saved.getId()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/products/{id}", saved.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("없는 상품 삭제는 404")
        void deleteNotFound() throws Exception {
            mockMvc.perform(delete("/api/products/{id}", 9999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        }
    }
}
