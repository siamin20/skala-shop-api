# skala-shop-api

온라인 쇼핑몰 백엔드 REST API.

상품·고객·주문을 계층형 구조로 구현하고, 재고와 선착순 이벤트에서 발생하는 동시성 문제를
낙관적 락과 비관적 락으로 나누어 해결한 뒤 그 결과를 수치로 검증한다.

## 기술 스택

- Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA
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

## 실행

```bash
docker compose -f infra/compose.yaml up -d
./gradlew bootRun
```

API 문서는 실행 후 `http://localhost:8080/swagger-ui.html`에서 확인한다.

## 문서

설계 문서(요구사항·도메인 설계·API 명세·동시성 전략·기술 결정 기록·로드맵)는
저장소에 포함하지 않고 로컬 `docs/`에서 관리한다.
