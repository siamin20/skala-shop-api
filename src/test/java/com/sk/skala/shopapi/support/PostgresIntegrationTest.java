package com.sk.skala.shopapi.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL 컨테이너 위에서 도는 통합 테스트의 부모 클래스.
 *
 * <p>나머지 테스트는 H2를 쓴다. 빠르고 컨테이너가 필요 없어 개발 중 반복 실행에 알맞다.
 * 하지만 H2는 {@code MODE=PostgreSQL}을 켜도 <b>PostgreSQL 흉내를 낼 뿐</b>이라
 * 두 DB의 차이는 드러나지 않는다.
 *
 * <p>실제로 이 클래스를 만들자마자 마이그레이션 하나가 PostgreSQL에서 실패했다.
 * H2에서는 166개 테스트가 모두 통과하고 있었으므로, 이 harness가 없었다면
 * <b>운영 배포에서 처음 발견됐을 결함</b>이다. 자세한 내용은 D21에 있다.
 *
 * <h2>컨테이너를 한 개만 띄우는 이유</h2>
 *
 * <p>{@code @Testcontainers} + {@code @Container}를 쓰면 <b>테스트 클래스마다</b>
 * 컨테이너가 뜨고 내려간다. PostgreSQL 기동은 2~3초씩 걸려서 클래스가 늘어날수록
 * 그만큼 곱해진다.
 *
 * <p>그래서 static 필드에 하나만 두고 {@code stop()}을 호출하지 않는다.
 * 테스트 JVM 전체에서 컨테이너 하나를 공유하며, 정리는 Testcontainers가 함께 띄우는
 * Ryuk 컨테이너가 JVM 종료를 감지해 대신 해준다.
 *
 * <p>{@link org.testcontainers.containers.GenericContainer#start()}는 이미 떠 있으면
 * 바로 반환하므로, 하위 클래스마다 호출해도 두 번 뜨지 않는다.
 *
 * <h2>Docker가 없으면 건너뛴다</h2>
 *
 * <p>{@link DockerAvailableCondition}이 Docker 가용 여부를 먼저 확인해,
 * 쓸 수 없으면 실패가 아니라 건너뜀으로 처리한다. 그 판단을 왜 애노테이션이 아니라
 * 확장으로 옮겼는지는 그 클래스의 문서 주석에 적어두었다.
 *
 * @see com.sk.skala.shopapi.support.MigrationCompatibilityTest
 */
@SpringBootTest
@ActiveProfiles("postgres")
// @EnabledIf가 아니라 확장으로 등록한다. JUnit의 @EnabledIf는 상속되지 않아
// 부모 클래스에 달면 조용히 무시된다. Docker를 꺼보고서야 알았다.
@ExtendWith(DockerAvailableCondition.class)
public abstract class PostgresIntegrationTest {

    /**
     * 태그를 고정한다. {@code postgres:latest}로 두면 어제 통과한 테스트가
     * 오늘 이미지가 바뀌었다는 이유로 깨질 수 있다. 무엇이 바뀌었는지도 알 수 없다.
     *
     * <p>{@code -alpine}은 이미지가 작아 최초 내려받기가 빠르다.
     */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    /** 테스트 JVM 전체가 공유하는 단 하나의 컨테이너. 위 문서 주석 참고. */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("skalashop")
            .withUsername("skalashop")
            .withPassword("skalashop");

    /**
     * Redis. 선착순 이벤트의 원자 카운터 전략이 쓴다. (D23)
     *
     * <p>Redis를 쓰지 않는 테스트에도 함께 띄운다. 별도 부모 클래스로 나누면 프로퍼티가
     * 달라져 <b>스프링 컨텍스트가 두 벌</b>이 되고, 그때마다 Flyway가 다시 돌아 전체가 느려진다.
     * 컨테이너 하나를 더 띄우는 비용이 컨텍스트를 하나 더 만드는 비용보다 싸다.
     *
     * <p>Redis 전용 모듈 대신 {@link GenericContainer}를 쓴다. Testcontainers 공식 BOM에
     * Redis 모듈이 없고, 포트 하나만 열면 되는 단순한 경우라 굳이 외부 모듈을 더할 이유가 없다.
     */
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    /**
     * 컨테이너를 띄우고 그 접속 정보를 스프링 설정에 주입한다.
     *
     * <p>포트를 고정하지 않고 컨테이너가 받은 임의 포트를 그대로 쓴다.
     * 고정하면 로컬에서 이미 5432를 쓰고 있을 때 충돌하고, CI에서 테스트를
     * 병렬로 돌릴 수도 없다.
     *
     * <p>{@code @DynamicPropertySource}는 <b>스프링 컨텍스트가 만들어지기 전에</b>
     * 실행된다. 그래서 여기서 컨테이너를 띄우면 DataSource가 만들어질 때
     * 이미 접속 가능한 상태가 된다.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
