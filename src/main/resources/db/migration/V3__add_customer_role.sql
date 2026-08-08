-- V3: 고객 역할 추가
--
-- 인가를 적용하려면 "이 요청자가 관리자인가"를 판단할 근거가 필요하다. (D17)
-- 명세에는 관리자 개념이 없지만, 상품 등록·삭제를 아무나 할 수 있는 상태를 두고
-- 인가를 구현했다고 할 수는 없다.
--
-- 기존 행에는 CUSTOMER를 채운다. NOT NULL 제약을 먼저 걸면 기존 행이 걸리므로
-- 컬럼 추가 → 값 채우기 → 제약 적용 순서로 진행한다.
-- 지금은 데이터가 없지만 순서를 지키는 편이 나중에 같은 패턴을 쓸 때 안전하다.
ALTER TABLE customer ADD COLUMN customer_role VARCHAR(20);

UPDATE customer SET customer_role = 'CUSTOMER' WHERE customer_role IS NULL;

ALTER TABLE customer ALTER COLUMN customer_role SET NOT NULL;
