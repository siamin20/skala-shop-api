# 기여 가이드

1인 프로젝트이지만 이력을 읽을 수 있게 남기기 위한 규칙이다.

## 브랜치

- `main` — 동작이 보장되는 기본 브랜치
- `feat/{도메인}/{기능}` — 기능 개발. 예: `feat/order/place-order`
- `fix/{도메인}/{내용}` — 오류 수정
- `docs/{주제}`, `chore/{주제}`, `test/{주제}`, `perf/{주제}`

Phase 하나를 브랜치 하나로 잡지 않는다. Phase 안의 의미 있는 단위마다 브랜치를 나눈다.

## 커밋 메시지

Gitmoji와 Conventional Commits를 함께 쓴다. 설명은 한국어로 쓴다.

```
<이모지> <type>(<scope>): <설명>
```

| type | 이모지 | 쓰는 때 |
| --- | --- | --- |
| `feat` | ✨ | 새 기능. 사용자에게 보이는 동작이 늘어날 때만 쓴다 |
| `fix` | 🐛 | 버그 수정 |
| `refactor` | ♻️ | 동작을 바꾸지 않는 구조 개선 |
| `perf` | ⚡️ | 성능 개선 |
| `test` | ✅ | 테스트 추가·수정 |
| `docs` | 📝 | 문서 |
| `build` | 📦 | Gradle, 의존성, Dockerfile |
| `ci` | 👷 | GitHub Actions |
| `chore` | 🔧 | 설정, 잡무 |
| `security` | 🔒 | 보안 관련 변경 |

scope는 도메인 이름(`product`, `customer`, `order`, `flashsale`) 또는
`global`, `infra`를 쓴다.

```
✨ feat(order): 상품 주문 및 취소 API 구현
♻️ refactor(customer): 포인트 차감 규칙을 도메인 메서드로 이동
⚡️ perf(flashsale): 이벤트 수량 차감을 Redis 원자 연산으로 전환
✅ test(order): 동시 주문 100건 정합성 테스트 추가
📝 docs: 락 전략 비교 측정 결과 기록
📦 build: Testcontainers PostgreSQL 의존성 추가
```

### 주의

- `feat`을 남용하지 않는다. 구조를 고쳤으면 `refactor`, 빨라졌으면 `perf`, 테스트면 `test`다.
- `wip`, `update`, `수정`, `임시` 같은 메시지는 쓰지 않는다.
- 하나의 커밋은 되돌렸을 때 말이 되는 단위로 만든다. 기능과 그 테스트는 한 커밋에 담는다.

## 규칙

- 비밀키, 비밀번호, 토큰을 커밋하지 않는다. 설정은 `.env.example`에만 예시로 남긴다.
- 교재 명세와 다르게 구현하면 로컬 `docs/05-decisions.md`에 근거를 남긴다.
- 측정으로 검증한 내용은 수치를 문서에 남긴다.
