# skala-shop-api 컨테이너 이미지
#
# 과제 명세 559p의 배포 다이어그램을 그대로 만족한다.
#   docker build -t shop-api:1.0 .
#   docker run -p 8080:8080 shop-api:1.0
# 기본 프로파일이 local이라 H2 내장으로 단독 실행된다. 외부 DB가 없어도 뜬다.
#
# 그 위에 세 가지를 더 얹었다. (D24)
#   1. 멀티 스테이지 — 빌드 도구를 최종 이미지에서 뺀다
#   2. Layered JAR   — 코드만 바뀌었을 때 의존성 레이어를 재사용한다
#   3. 비루트 실행   — 컨테이너가 뚫려도 root를 내주지 않는다
#
# 이미지 크기 비교는 infra/Dockerfile.single-stage와 docs/06-roadmap.md에 있다.


# ══════════════════ 1단계: 빌드 ══════════════════
#
# JDK가 필요한 단계다. 이 단계의 결과물 중 jar 하나만 다음 단계로 넘어가고
# Gradle, 소스, 캐시는 전부 버려진다. 최종 이미지에 남지 않는다.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 의존성 정의만 먼저 복사한다.
#
# 소스와 함께 복사하면 코드 한 줄만 고쳐도 이 레이어가 무효화되어
# 의존성을 매번 다시 내려받는다. 빌드 정의는 소스보다 훨씬 덜 바뀌므로
# 따로 떼어 캐시 적중률을 높인다.
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./

# 의존성만 먼저 받아 레이어로 굳힌다.
# 실패해도 넘어가는 이유: 오프라인 환경에서 이 단계가 막혀도
# 아래 bootJar가 다시 시도하므로 빌드 전체를 세울 이유가 없다.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src/ src/

# 테스트는 건너뛴다. 이미지 빌드는 CI가 테스트를 통과시킨 뒤에 하는 단계이고,
# 여기서 또 돌리면 Testcontainers가 컨테이너 안에서 컨테이너를 띄우려 한다. (P7)
RUN ./gradlew bootJar --no-daemon -x test


# ══════════════════ 2단계: 레이어 분해 ══════════════════
#
# fat jar를 통째로 COPY하면 이미지 레이어도 하나다. 코드 한 줄만 고쳐도
# 수십 MB짜리 레이어 전체가 새로 만들어지고 그만큼 다시 전송된다.
#
# layertools가 jar를 변경 빈도별로 넷으로 나눈다.
#   dependencies          — 거의 안 바뀜 (가장 크다)
#   spring-boot-loader    — 거의 안 바뀜
#   snapshot-dependencies — 가끔 바뀜
#   application           — 매번 바뀜 (가장 작다)
FROM eclipse-temurin:21-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract


# ══════════════════ 3단계: 실행 ══════════════════
#
# JDK가 아니라 JRE다. 컴파일러와 개발 도구가 빠져 이미지가 작아진다.
# 실행에는 필요 없고, 이미지에 없으면 침입자도 쓸 수 없다.
#
# Java 21로 실행한다. 바이트코드는 17로 컴파일하므로(D11) 17에서도 돌지만,
# 개발·테스트에서 쓰는 런타임과 같은 버전으로 맞춘다.
FROM eclipse-temurin:21-jre-alpine

# 비루트 사용자. 기본값인 root로 두면 컨테이너 격리가 뚫렸을 때
# 호스트 자원에 손댈 여지가 커진다. 애플리케이션에 root가 필요할 이유가 없다.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# 변경 빈도가 낮은 것부터 복사한다. 순서가 중요하다.
# 자주 바뀌는 application을 먼저 두면 그 뒤 레이어가 전부 무효화된다.
COPY --from=extractor --chown=app:app /extract/dependencies/ ./
COPY --from=extractor --chown=app:app /extract/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /extract/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /extract/application/ ./

USER app

EXPOSE 8080

# 컨테이너 안에서 본 메모리 한도를 JVM이 인식하게 한다.
#
# 이 설정이 없으면 JVM이 호스트 전체 메모리를 기준으로 힙을 잡는다.
# 컨테이너 한도를 넘기는 순간 OOMKilled로 죽는데, 로그에 아무것도 남지 않아
# 원인을 찾기 어렵다. 자바 예외가 아니라 커널이 프로세스를 죽이기 때문이다.
#
# 75%인 이유는 나머지 25%가 힙 바깥에서 쓰이기 때문이다.
# 메타스페이스, 스레드 스택, 코드 캐시, 네이티브 버퍼가 거기서 나온다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# 헬스체크. 오케스트레이터 없이 docker run만으로 띄웠을 때도 상태를 알 수 있다.
# alpine에 curl은 없지만 busybox wget은 있어서 추가 설치가 필요 없다.
#
# start-period를 40초로 둔 이유: 스프링 기동에 시간이 걸리는데 그 사이의 실패를
# 세지 않기 위해서다. 없으면 기동 중에 unhealthy로 판정된다.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health/liveness || exit 1

# JarLauncher를 직접 부른다. jar를 풀어놨으므로 java -jar를 쓸 수 없다.
# 경로가 Spring Boot 3.2에서 org.springframework.boot.loader.launch 로 옮겨졌다.
#
# sh -c로 감싸는 이유는 JAVA_OPTS를 펼치기 위해서다. exec를 붙여야
# 자바가 PID 1이 되어 docker stop의 SIGTERM을 직접 받고 정상 종료한다.
# 없으면 셸이 신호를 삼켜 10초 뒤 SIGKILL로 강제 종료된다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
