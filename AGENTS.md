# 작업 규칙

이 저장소에서 AI 에이전트가 따르는 규칙이다.

## 기준 문서

설계 문서는 저장소에 커밋하지 않고 로컬 `docs/`에만 둔다. 작업 전에 관련 문서를 먼저 읽는다.

- 과제가 요구하는 범위 — `docs/01-requirements.md`
- 도메인 모델과 패키지 구조 — `docs/02-domain-design.md`
- API 경로와 에러 규약 — `docs/03-api.md`
- 락 전략 — `docs/04-concurrency.md`
- 교재와 다르게 간 이유 — `docs/05-decisions.md`
- 진행 순서 — `docs/06-roadmap.md`
- 브랜치·커밋 규칙 — `CONTRIBUTING.md`

## 진행 방식

- `docs/06-roadmap.md`의 Phase 순서를 따른다. 앞선 Phase를 건너뛰지 않는다.
- 한 번에 하나의 Phase만 진행하고, 끝나면 로드맵의 체크박스를 갱신한다.
- 요청받지 않은 범위를 임의로 확장하지 않는다. 필요하면 멈추고 설명한 뒤 확인을 받는다.
- 더 나은 방법이 있으면 임의로 바꾸지 않고 대안을 설명한 뒤 확인을 받는다.

## 코드 규칙

- 도메인 객체에 Setter를 만들지 않는다. 상태 변경은 의미 있는 메서드로만 한다.
- 엔티티를 `@RequestBody`나 응답 본문으로 쓰지 않는다.
- 금액은 `Money`로 다룬다. `Double`이나 원시 타입으로 계산하지 않는다.
- 주문 주체는 항상 JWT에서 식별한다. 요청 본문의 `customerId`를 신뢰하지 않는다.
- 락 획득 순서는 `FlashSale → Product → Customer`를 지킨다.
- 낙관적 락 재시도는 트랜잭션 바깥 계층에서 한다.

## 주석

이 저장소의 코드는 나중에 다시 읽으며 공부하기 위한 것이다. 주석을 아끼지 않는다.

### 파일 최상단 문서 주석

모든 클래스와 인터페이스에 Javadoc을 단다. 무엇인지, 왜 존재하는지, 관련 문서가 어디인지 쓴다.

```java
/**
 * 고객이 주문한 상품 한 건을 나타내는 엔티티.
 *
 * <p>Customer와 Product를 잇는 매핑 엔티티이며, 같은 상품을 다시 주문하면
 * 새 행을 만들지 않고 {@link #increase(int)}로 수량만 누적한다.
 * 취소로 수량이 0이 되면 서비스가 이 항목을 삭제한다.
 *
 * <p>{@code unitPrice}는 주문 시점의 단가를 복사해 둔 값이다. 상품 가격이
 * 나중에 바뀌어도 이미 주문한 건의 환불 금액이 흔들리지 않게 하기 위해서다.
 *
 * @see docs/02-domain-design.md
 */
```

### 메서드 주석

공개 메서드에는 Javadoc을 단다. **무엇을 하는지보다 왜 그렇게 하는지를 쓴다.**

```java
/**
 * 보유 포인트에서 금액을 차감한다.
 *
 * <p>잔액 검사를 이 메서드 안에서 하는 이유는, 검사를 호출하는 쪽에 맡기면
 * 검사를 빠뜨린 경로가 하나만 생겨도 잔액이 음수가 되기 때문이다.
 *
 * @param amount 차감할 금액
 * @throws BusinessException 잔액이 부족한 경우 {@code INSUFFICIENT_POINT}
 */
```

### 본문 주석

의도가 코드에 드러나지 않는 곳에만 단다. 코드를 한국어로 번역하지 않는다.

```java
// ❌ 코드를 그대로 옮긴 주석
// customer의 point를 차감한다
customer.deductPoint(total);

// ✅ 코드에 안 보이는 이유를 남긴 주석
// 락 순서는 항상 Product -> Customer. 뒤집으면 취소 경로와 교차 데드락이 난다.
Product product = productRepository.findByIdForUpdate(productId);
Customer customer = customerRepository.findById(customerId);
```

### 규칙

- 설계 의도, 트레이드오프, 함정은 반드시 남긴다. 특히 락 순서, 트랜잭션 경계, 재시도 위치.
- 교재와 다르게 구현한 지점에는 `docs/05-decisions.md`의 결정 번호를 적는다. 예: `// D2: Setter 대신 도메인 메서드`
- 설정 파일(`build.gradle`, `application.yml`, `Dockerfile`)에도 각 블록이 왜 필요한지 주석을 단다.
- 주석과 코드가 어긋나면 주석을 고친다. 틀린 주석은 없는 것보다 나쁘다.

## 문서

- 교재 명세와 다르게 구현하면 `docs/05-decisions.md`에 결정과 근거를 추가한다.
- 성능이나 정합성을 측정했으면 수치를 문서에 남긴다. 추정치를 결과처럼 쓰지 않는다.

## 커뮤니케이션

- 대화와 설명은 한국어로 한다.
- 작업을 마치면 무엇을 했고 무엇을 검증했는지 간단히 알린다.
- 테스트가 실패했거나 건너뛴 단계가 있으면 그대로 말한다.
