package com.sk.skala.shopapi.delivery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.delivery.app.DeliveryAddressService;
import com.sk.skala.shopapi.delivery.dto.DeliveryAddressRequest;
import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.support.WithMockCustomer;

/**
 * 배송지 API. (D34, D49)
 *
 * <p>이 테스트가 늦게 생겼다. 서비스 계층은 다른 테스트가 쓰고 있었지만
 * <b>HTTP 경로로 부르는 테스트가 하나도 없었다.</b> 경로 점검을 돌려보고 알았다.
 *
 * <p>서비스가 맞아도 컨트롤러에서 틀릴 수 있는 것이 있다. 대표적으로 <b>소유권</b>이다.
 * 배송지에는 주소·연락처·공동현관 비밀번호가 들어 있다. 남의 것을 읽거나 고칠 수 있으면
 * 그건 기능 결함이 아니라 개인정보 사고다.
 *
 * <p>경로에 고객 아이디가 없다는 설계(D6)가 실제로 지켜지는지도 여기서 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("배송지 API")
class DeliveryAddressControllerTest {

    private static final String PATH = "/api/delivery-addresses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryAddressService deliveryAddressService;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAllInBatch();
        customerRepository.save(new Customer("owner01", "$2a$10$h", Money.of(100_000)));
        customerRepository.save(new Customer("other01", "$2a$10$h", Money.of(100_000)));
    }

    private String body(String label, String zipcode, String address, String entrancePassword) {
        return """
                {"label":"%s","recipient":"신민서","phone":"01012345678",
                 "zipcode":"%s","address":"%s","addressDetail":"3층","entrancePassword":%s}
                """.formatted(label, zipcode, address,
                entrancePassword == null ? "null" : "\"" + entrancePassword + "\"");
    }

    /** 배송지를 하나 만들고 그 id를 돌려준다. */
    private long 등록(String label, String zipcode, String address) throws Exception {
        String json = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(label, zipcode, address, "1234*")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Nested
    @DisplayName("본인 배송지")
    @WithMockCustomer("owner01")
    class Mine {

        @Test
        @DisplayName("등록하면 첫 배송지가 자동으로 기본이 된다")
        void firstBecomesDefault() throws Exception {
            등록("집", "13529", "경기 성남시 분당구 판교역로 166");

            mockMvc.perform(get(PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].label").value("집"))
                    .andExpect(jsonPath("$[0].isDefault").value(true));
        }

        @Test
        @DisplayName("여러 개를 등록해도 기본은 하나뿐이고 목록 맨 앞에 온다")
        void onlyOneDefault() throws Exception {
            등록("집", "13529", "경기 성남시 분당구 판교역로 166");
            등록("회사", "04524", "서울 중구 세종대로 110");

            mockMvc.perform(get(PATH))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].isDefault").value(true))
                    .andExpect(jsonPath("$[1].isDefault").value(false));
        }

        @Test
        @DisplayName("공동현관 비밀번호는 값이 아니라 등록 여부만 내려준다")
        void doesNotLeakEntrancePassword() throws Exception {
            등록("집", "13529", "경기 성남시 분당구 판교역로 166");

            String json = mockMvc.perform(get(PATH))
                    .andExpect(jsonPath("$[0].hasEntrancePassword").value(true))
                    .andReturn().getResponse().getContentAsString();

            // 목록에 실려 나가면 화면을 보는 것만으로 남의 현관이 열린다.
            org.assertj.core.api.Assertions.assertThat(json)
                    .as("공동현관 비밀번호 값이 응답에 섞여 나왔다")
                    .doesNotContain("1234*");
        }

        @Test
        @DisplayName("수정하면 반영된다")
        void update() throws Exception {
            long id = 등록("집", "13529", "경기 성남시 분당구 판교역로 166");

            mockMvc.perform(put(PATH + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("본가", "48058", "부산 해운대구 해운대로 3", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("본가"))
                    .andExpect(jsonPath("$.hasEntrancePassword").value(false));
        }

        @Test
        @DisplayName("기본 배송지를 지우면 남은 것 중 하나가 기본이 된다")
        void deletingDefaultPromotesAnother() throws Exception {
            long 집 = 등록("집", "13529", "경기 성남시 분당구 판교역로 166");
            등록("회사", "04524", "서울 중구 세종대로 110");

            mockMvc.perform(delete(PATH + "/" + 집)).andExpect(status().isNoContent());

            // 기본이 하나도 없으면 결제 화면이 아무것도 고르지 못한다.
            mockMvc.perform(get(PATH))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].label").value("회사"))
                    .andExpect(jsonPath("$[0].isDefault").value(true));
        }

        @Test
        @DisplayName("우편번호 형식이 틀리면 400")
        void rejectsBadZipcode() throws Exception {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("집", "135", "경기 성남시", null)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("남의 배송지")
    @WithMockCustomer("other01")
    class NotMine {

        /**
         * owner01의 배송지를 서비스로 직접 만들어 둔다.
         *
         * <p>HTTP로 만들면 요청 주체가 other01이라 자기 것이 되어버린다.
         * 공격자가 남의 id를 알아냈다고 가정하는 상황을 만들려면 소유자를 달리해야 한다.
         * id는 연속된 숫자라 추측하기 어렵지 않다.
         */
        private long 남의배송지() {
            return deliveryAddressService.add("owner01", new DeliveryAddressRequest(
                    "집", "신민서", "01012345678", "13529",
                    "경기 성남시 분당구 판교역로 166", "3층", "1234*", null)).id();
        }

        @Test
        @DisplayName("목록에는 자기 것만 나온다")
        void listShowsOnlyOwn() throws Exception {
            남의배송지();

            mockMvc.perform(get(PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("id를 알아도 수정할 수 없다")
        void cannotUpdateOthers() throws Exception {
            long id = 남의배송지();

            mockMvc.perform(put(PATH + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("가로챔", "04524", "서울 중구 세종대로 110", null)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("id를 알아도 삭제할 수 없다")
        void cannotDeleteOthers() throws Exception {
            long id = 남의배송지();

            mockMvc.perform(delete(PATH + "/" + id))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    @WithAnonymousUser
    void requiresLogin() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    /** 응답에 실제로 무엇이 담기는지 한 번은 눈으로 확인해 둔다. */
    @Test
    @DisplayName("응답 필드 구성")
    @WithMockCustomer("owner01")
    void responseShape() throws Exception {
        등록("집", "13529", "경기 성남시 분당구 판교역로 166");

        String json = mockMvc.perform(get(PATH)).andReturn().getResponse().getContentAsString();
        JsonNode first = objectMapper.readTree(json).get(0);

        org.assertj.core.api.Assertions.assertThat(first.fieldNames())
                .toIterable()
                .contains("id", "label", "recipient", "phone", "zipcode",
                        "address", "addressDetail", "fullAddress",
                        "hasEntrancePassword", "isDefault");
    }
}
