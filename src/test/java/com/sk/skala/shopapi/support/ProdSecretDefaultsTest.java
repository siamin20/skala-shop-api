package com.sk.skala.shopapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 운영 프로파일의 비밀값에 기본값이 남아 있지 않은지 확인한다. (D26)
 *
 * <p><b>이 저장소는 공개되어 있다.</b> 설정 파일에 {@code skalashop/skalashop} 같은 값을
 * 적어두면 그건 "예시"가 아니라 누구나 아는 자격 증명이 된다. 환경변수 설정을 한 번
 * 빠뜨리면 운영 DB가 그 값으로 뜨고, <b>기동이 성공하기 때문에 아무도 알아채지 못한다.</b>
 *
 * <p>JWT 서명 키에는 이 원칙을 적용해 놓고 DB 자격 증명은 남겨뒀었다. 같은 실수가
 * 다음에 또 나올 수 있어서, 사람의 주의력 대신 테스트로 막는다.
 *
 * <h2>왜 기동 테스트가 아니라 설정 파일을 읽는가</h2>
 *
 * <p>"prod로 띄워보고 실패하는지 본다"가 더 직접적이지만, 그러려면 테스트가
 * <b>실제 PostgreSQL에 접속을 시도</b>하게 된다. 접속 실패와 "기본값이 박혀 있어서
 * 뜨면 안 되는데 떴다"를 구분하기 어렵고, DB가 없는 환경에서는 항상 통과한다.
 *
 * <p>확인하려는 규칙 자체는 설정 파일에 적혀 있다. 그래서 파일을 읽는다.
 *
 * <h2>local 프로파일은 검사하지 않는다</h2>
 *
 * <p>개발용 H2 계정에는 기본값이 있어야 한다. 없으면 저장소를 받은 사람이
 * 환경변수부터 찾아야 한다. 위험한 것은 <b>운영에 나가는 값</b>이지 기본값 자체가 아니다.
 */
@DisplayName("운영 설정에 비밀값 기본값이 없다")
class ProdSecretDefaultsTest {

    /** 기본값이 있으면 안 되는 설정 키. 점으로 구분한 경로다. */
    private static final List<String> MUST_NOT_HAVE_DEFAULT = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password");

    @Test
    @DisplayName("prod 프로파일의 DB 접속 정보에 기본값이 없다")
    void prodDatasourceHasNoDefaults() {
        Map<String, Object> prod = profileDocument("prod");

        assertThat(prod)
                .as("application.yml에서 prod 프로파일 블록을 찾지 못했다")
                .isNotNull();

        for (String key : MUST_NOT_HAVE_DEFAULT) {
            String value = String.valueOf(read(prod, key));

            // ${VAR}는 통과하고 ${VAR:기본값}은 실패한다.
            // 콜론 뒤에 무엇이 오든 그것이 환경변수 없이 쓰이는 값이다.
            assertThat(value)
                    .as("%s 에 기본값이 남아 있다. 공개 저장소이므로 기본값은 곧 공개된 자격 증명이다", key)
                    .matches("\\$\\{[A-Z0-9_]+}");
        }
    }

    @Test
    @DisplayName("prod 프로파일은 스키마를 검증만 한다")
    void prodDoesNotAlterSchema() {
        Map<String, Object> prod = profileDocument("prod");

        // ddl-auto가 update면 엔티티만 고쳐도 운영 테이블이 따라 바뀐다. (D16)
        // 무슨 DDL이 나갈지 미리 알 수 없고, 마이그레이션을 빠뜨려도 조용히 넘어간다.
        assertThat(read(prod, "spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
    }

    // ── 아래는 YAML을 읽는 잡일 ──

    /** 여러 문서로 나뉜 application.yml에서 해당 프로파일 블록을 찾는다. */
    private Map<String, Object> profileDocument(String profile) {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            for (Object document : new Yaml().loadAll(in)) {
                if (!(document instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) document;
                if (profile.equals(read(map, "spring.config.activate.on-profile"))) {
                    return map;
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("application.yml을 읽지 못했다", e);
        }
    }

    /** {@code a.b.c} 형태의 경로로 중첩 맵을 따라 내려간다. */
    private Object read(Map<String, Object> root, String path) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(segment);
        }
        return current;
    }

    /** 검사 대상 키를 늘릴 때 오타를 바로 알아채려고 둔다. */
    @Test
    @DisplayName("검사 대상 키가 실제로 존재한다")
    void keysExist() {
        Map<String, Object> prod = profileDocument("prod");
        List<String> missing = new ArrayList<>();

        for (String key : MUST_NOT_HAVE_DEFAULT) {
            if (read(prod, key) == null) {
                missing.add(key);
            }
        }

        // 키 이름을 잘못 적으면 read()가 null을 돌려주고, 위 테스트는 "기본값 없음"으로
        // 통과해 버린다. 아무것도 검사하지 않으면서 초록불이 켜지는 상태다.
        assertThat(missing).as("설정에 없는 키를 검사하고 있다").isEmpty();
    }

    /** 중첩 맵 헬퍼가 실제로 동작하는지. */
    @Test
    @DisplayName("경로 탐색이 없는 키에 대해 null을 준다")
    void readReturnsNullForMissing() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", new LinkedHashMap<>(Map.of("b", "값")));

        assertThat(read(root, "a.b")).isEqualTo("값");
        assertThat(read(root, "a.b.c")).isNull();
        assertThat(read(root, "없는키")).isNull();
    }
}
