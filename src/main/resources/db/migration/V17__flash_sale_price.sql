-- V17: 특가 판매가
--
-- "특가"라고 부르면서 정가 그대로 팔고 있었다. (D42)
-- 한정 수량만 걸어두고 가격은 같으니, 사용자 입장에서는 특가가 아니라 그냥 재고 제한이다.
--
-- 실제 커머스의 특가는 **할인율이 먼저 눈에 들어온다.** 그것이 참여 이유이기 때문이다.
-- 남은 수량은 급박함을 만들고, 할인율은 이득을 만든다. 둘 다 있어야 한다.

-- 특가 판매가. 상품 정가와 별개로 이 이벤트에서만 적용된다.
ALTER TABLE flash_sale ADD COLUMN sale_price BIGINT;

-- 기존 이벤트에 할인가를 채운다. 정가의 60~75% 수준으로, 이벤트마다 다르게 준다.
-- 전부 같은 비율이면 화면에서 할인율이 한 값으로만 보여 비교가 되지 않는다.
UPDATE flash_sale fs SET sale_price = (
    SELECT CAST(p.product_price * 0.6 AS BIGINT) FROM product p WHERE p.id = fs.product_id
) WHERE fs.sale_name LIKE '%골드 앰플%';

UPDATE flash_sale fs SET sale_price = (
    SELECT CAST(p.product_price * 0.7 AS BIGINT) FROM product p WHERE p.id = fs.product_id
) WHERE fs.sale_name LIKE '%아이크림%';

UPDATE flash_sale fs SET sale_price = (
    SELECT CAST(p.product_price * 0.75 AS BIGINT) FROM product p WHERE p.id = fs.product_id
) WHERE fs.sale_name LIKE '%레티놀%';

-- 나머지는 정가 그대로.
UPDATE flash_sale fs SET sale_price = (
    SELECT p.product_price FROM product p WHERE p.id = fs.product_id
) WHERE fs.sale_price IS NULL;

ALTER TABLE flash_sale ALTER COLUMN sale_price SET NOT NULL;

-- 특가가 정가보다 비쌀 수는 없다. 데이터 입력 실수를 막는다.
-- 0원도 막는다. 무료 증정은 다른 기능이지 특가가 아니다.
ALTER TABLE flash_sale ADD CONSTRAINT ck_flash_sale_price CHECK (sale_price > 0);
