# skala-shop-api

온라인 쇼핑몰 백엔드 REST API.

상품·고객·주문을 계층형 구조로 구현하고, 재고와 선착순 이벤트에서 발생하는 동시성 문제를
낙관적 락과 비관적 락으로 나누어 해결한 뒤 그 결과를 수치로 검증한다.

## 기술 스택

- Java 17 (컴파일) / Java 21 (권장 런타임), Spring Boot 3.3.0, Spring Security, Spring Data JPA
- PostgreSQL (운영) / H2 (개발) / Redis (선착순 이벤트 카운터)
- Gradle, JUnit 5, Testcontainers
- Docker, Kubernetes, GitHub Actions

## 주요 설계

- 금액은 `Money` 값 객체로 다룬다. 부동소수점 오차를 피하기 위해 내부는 원 단위 정수다.
- 도메인 객체에 Setter를 두지 않는다. 상태 변경은 `deductPoint`, `decreaseStock` 같은 메서드로만 한다.
- 엔티티를 요청·응답 본문으로 쓰지 않는다. 요청은 Request DTO로 받아 검증한다.
- 에러 응답은 RFC 7807 `ProblemDetail`을 따른다.
- 자원마다 락 전략을 달리한다. 포인트는 낙관적 락과 재시도, 재고는 비관적 락,
  선착순 이벤트 수량은 세 방식을 비교해 결정한다.
- 데드락을 막기 위해 락 획득 순서를 `FlashSale → Product → Customer`로 고정한다.

## 실행 요건

| 항목 | 값 |
| --- | --- |
| Java | **17 이상 21 이하** (17.0.20 · 21.0.12에서 검증) |
| Gradle | 저장소의 Wrapper(8.14.3)를 쓰므로 설치 불필요 |
| Docker | PostgreSQL·Redis 실행용. H2만 쓸 경우 없어도 된다 |

컴파일 대상이 Java 17이라 JDK 17 환경에서도 그대로 빌드·실행된다.
Wrapper의 Gradle 8.14.3이 지원하지 않는 최신 JDK는 쓸 수 없다.

### Java 17과 21의 차이

Java 21에서 `spring.threads.virtual.enabled=true`를 켜면 자동 구성되는
`TaskExecutor`와 `TaskScheduler`가 가상 스레드 기반 구현으로 바뀐다.
이때 스레드 풀 크기 같은 풀 관련 설정은 적용되지 않으며, 가상 스레드는 데몬 스레드이므로
예약 작업의 종료 동작도 달라진다.

이 실행기는 `@Async`, `@Scheduled`, 그리고 `Callable`·`DeferredResult`를 반환하는
비동기 MVC 요청 처리에도 쓰인다. 따라서 이들을 사용하는 코드가 있으면 두 버전의 동작이 달라질 수 있다.

Java 17에서는 이 속성이 조용히 무시되고 플랫폼 스레드 풀로 동작한다.

**현재 이 프로젝트는 `@Async`·`@Scheduled`·비동기 MVC 반환 타입을 쓰지 않는다.**
그래서 지금은 두 버전의 API 응답이 같다. 이후 비동기 처리를 도입하면 이 문장은 더 이상 성립하지 않으므로,
그 시점에 이 절을 갱신하고 두 런타임에서 다시 검증한다.

## 실행

애플리케이션은 호스트 JDK에서 실행된다. 아래 Compose 파일은 PostgreSQL과 Redis만 띄운다.
애플리케이션까지 컨테이너로 묶는 구성은 P6에서 추가한다.

```bash
docker compose -f infra/compose.yaml up -d   # PostgreSQL, Redis
./gradlew bootRun                            # 애플리케이션 (호스트 JDK)
```

H2만으로 띄우려면 Compose 없이 `./gradlew bootRun`만 실행하면 된다. 기본 프로파일이 `local`이다.

API 문서는 실행 후 `http://localhost:8080/swagger-ui.html`에서 확인한다.

## 문서

설계 문서(요구사항·도메인 설계·API 명세·동시성 전략·기술 결정 기록·로드맵)는
저장소에 포함하지 않고 로컬 `docs/`에서 관리한다.
