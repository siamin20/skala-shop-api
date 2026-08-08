-- V5: 재고와 낙관적 락 버전
--
-- P4에서 동시성 제어를 넣기 위해 필요한 두 컬럼이다. (D22)
--
-- 명세에는 재고 개념이 없다. 상품은 이름과 가격만 가진다.
-- 그런데 재고 없이는 "여러 사용자가 같은 행을 다투는" 상황 자체가 만들어지지 않아
-- 비관적 락을 쓸 자리가 없다. 포인트는 본인만 바꾸므로 남과 다툴 일이 없기 때문이다.
-- 락 전략을 비교하려면 경합하는 자원이 하나는 있어야 한다. (D8)

-- ─────────────────────────── 재고 ───────────────────────────
--
-- DEFAULT 0으로 컬럼을 추가하면 기존 행이 전부 품절 상태가 된다.
-- 그래서 컬럼 추가 → 값 채우기 → 기본값 제거 순서로 진행한다.
-- V3에서 customer_role을 추가할 때와 같은 패턴이다.
ALTER TABLE product ADD COLUMN product_stock INTEGER NOT NULL DEFAULT 0;

-- V2가 넣은 개발용 상품에 재고를 준다. 0으로 두면 기존 주문 시나리오가 전부 막힌다.
UPDATE product SET product_stock = 1000 WHERE product_stock = 0;

-- 기본값을 남겨두면 재고를 지정하지 않고 상품을 만들 수 있다.
-- 그러면 등록하자마자 품절인 상품이 조용히 생긴다. 명시적으로 넣게 강제한다.
ALTER TABLE product ALTER COLUMN product_stock DROP DEFAULT;

-- 재고는 음수가 될 수 없다. 애플리케이션이 먼저 검사하지만 그 검사를 빠뜨린 경로가
-- 하나만 생겨도 음수 재고가 만들어진다. 최종 방어선을 DB에 둔다.
ALTER TABLE product ADD CONSTRAINT ck_product_stock_not_negative CHECK (product_stock >= 0);

-- ────────────────────── 낙관적 락 버전 ──────────────────────
--
-- Hibernate가 UPDATE ... WHERE version = ? 로 갱신하고, 바뀐 행이 0이면
-- "내가 읽은 뒤 누군가 먼저 바꿨다"고 판단해 예외를 던진다.
--
-- 포인트는 본인만 바꾸므로 경합이 드물다. 드문 충돌 때문에 락 대기를 걸어
-- 처리량을 깎을 이유가 없다. 그래서 비관적 락이 아니라 낙관적 락이다. (D22)
ALTER TABLE customer ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 여기서는 기본값을 남겨둔다. 새 행의 버전은 Hibernate가 0으로 채우지만,
-- 마이그레이션이나 관리 스크립트가 직접 INSERT할 때 버전을 신경 쓰게 하고 싶지 않다.
