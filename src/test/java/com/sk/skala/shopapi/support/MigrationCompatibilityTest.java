package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 마이그레이션이 실제 PostgreSQL에서도 적용되는지 검증한다. (D21)
 *
 * <p>이 클래스가 존재하는 이유는 단순하다. <b>D16에서 "마이그레이션이 스키마의 유일한
 * 진실 소스"라고 정해놓고, 정작 그 마이그레이션을 실제 PostgreSQL에서 한 번도 돌려본 적이
 * 없었기 때문이다.</b> H2가 통과시킨 것을 PostgreSQL도 통과시킨다는 보장은 없다.
 *
 * <p>실제로 이 테스트를 처음 돌렸을 때 V4가 실패했다.
 * {@code response_body CLOB}은 H2에는 있는 타입이고 PostgreSQL에는 없다.
 * H2에서 166개가 모두 통과하는 동안 <b>{@code prod} 프로파일은 기동조차 못 하는 상태</b>였다.
 *
 * <h2>무엇을 검증하는가</h2>
 *
 * <p>가장 큰 검증은 <b>이 클래스가 뜬다는 사실 자체</b>다. 컨텍스트가 뜨려면
 * 마이그레이션 4개가 모두 적용되고, 그 결과 스키마를 Hibernate {@code validate}가
 * 통과시켜야 한다. 둘 중 하나라도 어긋나면 테스트 메서드에 도달하지 못한다.
 *
 * <p>나머지 검증은 "떴지만 조용히 잘못된" 경우를 잡는다.
 * 인코딩이 깨진 시드 데이터, 걸리지 않은 제약 같은 것들이다.
 */
@DisplayName("PostgreSQL 마이그레이션 호환성")
// 컨테이너를 테스트 전체가 공유하므로, 롤백하지 않으면 여기서 넣은 행이
// 다음 테스트에 남아 결과를 바꾼다. 제약 위반 테스트는 특히 그렇다.
@org.springframework.transaction.annotation.Transactional
class MigrationCompatibilityTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("마이그레이션 적용")
    class Migrations {

        @Test
        @DisplayName("클래스패스의 마이그레이션이 모두 적용되고 전부 성공이다")
        void allMigrationsSucceeded() {
            List<Map<String, Object>> history = jdbc.queryForList(
                    "SELECT version, description, success FROM flyway_schema_history "
                            + "WHERE version IS NOT NULL ORDER BY installed_rank");

            // 버전 번호를 못 박지 않는다. 스택 브랜치마다 갖고 있는 마이그레이션이 달라
            // 목록을 고정하면 병합할 때마다 이 테스트를 고쳐야 한다.
            // 확인해야 하는 것은 "클래스패스에 있는 것이 전부 적용됐는가"다.
            long onClasspath;
            try (var stream = java.nio.file.Files.list(
                    java.nio.file.Path.of("src/main/resources/db/migration"))) {
                onClasspath = stream.filter(f -> f.toString().endsWith(".sql")).count();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("마이그레이션 디렉터리를 읽을 수 없다", e);
            }

            assertThat(history).hasSize((int) onClasspath);
            assertThat(history).extracting(row -> row.get("version")).doesNotHaveDuplicates();

            // 하나라도 실패했다면 Flyway가 기동을 막았겠지만, 명시적으로 확인해둔다.
            assertThat(history).allSatisfy(row ->
                    assertThat(row.get("success")).isEqualTo(true));
        }

        @Test
        @DisplayName("도메인 테이블이 모두 만들어진다")
        void tablesExist() {
            List<String> tables = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' "
                            + "ORDER BY table_name",
                    String.class);

            assertThat(tables)
                    .containsExactly("customer", "delivery_address", "flash_sale",
                            "idempotency_key", "order_item", "order_line", "orders",
                            "payment", "product");
        }

        @Test
        @DisplayName("response_body는 Large Object가 아닌 일반 문자열 컬럼이다")
        void responseBodyIsNotALargeObject() {
            Map<String, Object> column = jdbc.queryForMap(
                    "SELECT data_type, character_maximum_length FROM information_schema.columns "
                            + "WHERE table_name = 'idempotency_key' AND column_name = 'response_body'");

            // oid가 나오면 Large Object로 저장되고 있다는 뜻이다. 그 경우 행을 지워도
            // 본문이 pg_largeobject에 남고, autocommit 모드에서는 읽지도 못한다. (D21)
            assertThat(column.get("data_type")).isEqualTo("character varying");

            // 길이 상한이 없어야 한다. 응답 JSON이 상한을 넘으면 저장이 실패하고,
            // 저장이 실패하면 재시도가 중복 실행되어 멱등성 보장 자체가 깨진다.
            assertThat(column.get("character_maximum_length")).isNull();
        }
    }

    @Nested
    @DisplayName("시드 데이터")
    class SeedData {

        @Test
        @DisplayName("한글 상품명이 그대로 들어간다")
        void koreanProductNamesSurvive() {
            List<String> names = jdbc.queryForList(
                    "SELECT product_name FROM product ORDER BY id", String.class);

            // 인코딩이 어긋나면 예외 없이 '???'나 깨진 글자로 저장된다.
            // 조용히 잘못되는 종류라 눈으로 확인할 기회가 없다.
            // V10이 명세 예시 상품을 뷰티 상품으로 교체했다. 행을 지우지 않고
            // 이름만 바꿔서 id와 주문 내역이 그대로 유지된다. (D29)
            assertThat(names)
                    .doesNotContain("무선마우스", "블루투스키보드", "USB허브")
                    .contains("수분 진정 토너패드 70매", "세라마이드 수분 크림 50ml",
                            "한정판 골드 앰플 세트");
        }

        @Test
        @DisplayName("V8이 넣은 선착순 이벤트 중 진행 중인 것이 있다")
        void openFlashSaleExists() {
            // 고정 날짜를 쓰면 배포 시점에 따라 이미 끝난 이벤트가 된다.
            // CURRENT_TIMESTAMP 기준 상대 시각으로 잡았는지 확인한다. (D29)
            Integer open = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flash_sale "
                            + "WHERE starts_at <= CURRENT_TIMESTAMP AND ends_at > CURRENT_TIMESTAMP",
                    Integer.class);

            assertThat(open).isEqualTo(3);
        }

        @Test
        @DisplayName("V5가 기존 상품에 재고를 채운다")
        void v5BackfillsStock() {
            // DEFAULT 0으로 컬럼만 추가하면 기존 상품이 전부 품절이 된다.
            // 컬럼 추가 → 값 채우기 → 기본값 제거 순서를 지켰는지 확인한다. (D22)
            List<Integer> stocks = jdbc.queryForList(
                    "SELECT product_stock FROM product ORDER BY id", Integer.class);

            assertThat(stocks).allSatisfy(stock -> assertThat(stock).isPositive());
        }

        @Test
        @DisplayName("가격이 정수 그대로 보존된다")
        void pricesArePreserved() {
            Long price = jdbc.queryForObject(
                    "SELECT product_price FROM product WHERE product_name = '수분 진정 토너패드 70매'",
                    Long.class);

            // BIGINT로 저장한다. 실수형이면 여기서 15000.0이 되거나 반올림 오차가 생긴다. (D1)
            assertThat(price).isEqualTo(18_000L);
        }
    }

    /**
     * 제약이 실제로 걸리는지 확인한다.
     *
     * <p>H2에서 통과했다고 PostgreSQL에서도 같은 제약이 걸린다고 볼 수는 없다.
     * 마이그레이션 문법이 조금만 달라도 제약이 <b>조용히 빠진 채</b> 테이블만 만들어진다.
     * 그 상태에서도 애플리케이션은 정상 동작하는 것처럼 보이므로 직접 위반해봐야 한다.
     */
    @Nested
    @DisplayName("제약 조건")
    class Constraints {

        @Test
        @DisplayName("같은 고객이 같은 상품으로 두 행을 만들 수 없다")
        void uniqueOrderItemPerCustomerAndProduct() {
            jdbc.update("INSERT INTO customer (customer_id, customer_password, customer_point, "
                    + "customer_role) VALUES ('uniq01', '$2a$10$h', 0, 'CUSTOMER')");
            Long productId = jdbc.queryForObject(
                    "SELECT id FROM product WHERE product_name = '수분 진정 토너패드 70매'", Long.class);

            jdbc.update("INSERT INTO order_item (customer_id, product_id, quantity, total_amount) "
                    + "VALUES ('uniq01', ?, 1, 15000)", productId);

            // 이 제약이 없으면 동시 요청 둘이 각자 "기존 항목 없음"을 확인하고
            // 행을 두 개 만든다. 애플리케이션 검사만으로는 막을 수 없는 경로다.
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO order_item (customer_id, product_id, quantity, total_amount) "
                            + "VALUES ('uniq01', ?, 1, 15000)", productId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("없는 고객의 주문 항목은 만들 수 없다")
        void orderItemRequiresExistingCustomer() {
            Long productId = jdbc.queryForObject(
                    "SELECT id FROM product WHERE product_name = '딥클렌징 오일 200ml'", Long.class);

            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO order_item (customer_id, product_id, quantity, total_amount) "
                            + "VALUES ('ghost99', ?, 1, 39000)", productId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("상품명은 중복될 수 없다")
        void productNameIsUnique() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO product (product_name, product_price, product_stock, "
                            + "category, subcategory) "
                            + "VALUES ('수분 진정 토너패드 70매', 1, 10, '기타', '기타')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("재고는 음수가 될 수 없다")
        void stockCannotBeNegative() {
            // 애플리케이션이 먼저 검사하지만 그 검사를 빠뜨린 경로가 하나만 생겨도
            // 음수 재고가 만들어진다. V5의 CHECK 제약이 최종 방어선이다. (D22)
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE product SET product_stock = -1 WHERE product_name = '수분 진정 토너패드 70매'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("customer_role은 NOT NULL이다")
        void customerRoleIsNotNull() {
            // V3이 컬럼 추가 → 값 채우기 → 제약 적용 순서로 진행했는데,
            // 마지막 ALTER가 PostgreSQL에서 실제로 먹었는지 확인한다.
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO customer (customer_id, customer_password, customer_point) "
                            + "VALUES ('norole01', '$2a$10$h', 0)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
