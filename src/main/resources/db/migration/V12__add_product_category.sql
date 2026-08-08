-- V12: 상품 카테고리
--
-- 상품이 19개가 되니 목록에서 원하는 것을 찾기 어려워졌다. (D35)
--
-- ── 왜 화면이 아니라 DB에 두는가 ──
--
-- 프론트에서 상품명을 정규식으로 분류할 수도 있다. 실제로 일러스트는 그렇게 고른다.
-- 하지만 분류를 화면에서 하면 **서버 페이지 처리와 어긋난다.**
-- 서버가 10개를 잘라 보낸 뒤 화면이 그중 3개만 걸러내면, 사용자는 "10개 보기"인데
-- 3개만 있는 페이지를 보게 된다. 필터와 페이지를 함께 쓰려면 같은 곳에서 해야 한다.
--
-- ── 왜 두 단계인가 ──
--
-- 대분류만 두면 "스킨케어"에 절반이 몰린다. 소분류가 있어야 실제로 좁혀진다.
-- 세 단계 이상은 이 규모에 과하다.

ALTER TABLE product ADD COLUMN category     VARCHAR(30);
ALTER TABLE product ADD COLUMN subcategory  VARCHAR(30);

-- 이름으로 채운다. 상품마다 손으로 지정하는 편이 정확하지만,
-- 기존 행에 값을 넣어야 NOT NULL을 걸 수 있어 규칙으로 일괄 처리한다.
-- 앞으로 등록되는 상품은 API에서 직접 받는다.
UPDATE product SET category = '스킨케어', subcategory = '토너·패드'
 WHERE product_name LIKE '%토너%' OR product_name LIKE '%패드%';

UPDATE product SET category = '스킨케어', subcategory = '앰플·세럼'
 WHERE product_name LIKE '%앰플%' OR product_name LIKE '%세럼%' OR product_name LIKE '%에센스%';

UPDATE product SET category = '스킨케어', subcategory = '크림·로션'
 WHERE category IS NULL
   AND (product_name LIKE '%크림%' OR product_name LIKE '%수딩젤%');

UPDATE product SET category = '스킨케어', subcategory = '마스크팩'
 WHERE category IS NULL AND (product_name LIKE '%마스크%' OR product_name LIKE '%팩%');

UPDATE product SET category = '클렌징·선케어', subcategory = '클렌징'
 WHERE category IS NULL
   AND (product_name LIKE '%클렌징%' OR product_name LIKE '%폼%' OR product_name LIKE '%오일%');

UPDATE product SET category = '클렌징·선케어', subcategory = '선케어'
 WHERE category IS NULL AND product_name LIKE '%선크림%';

UPDATE product SET category = '메이크업', subcategory = '립'
 WHERE category IS NULL AND (product_name LIKE '%립%' OR product_name LIKE '%틴트%');

UPDATE product SET category = '메이크업', subcategory = '베이스'
 WHERE category IS NULL
   AND (product_name LIKE '%쿠션%' OR product_name LIKE '%파운데이션%');

UPDATE product SET category = '메이크업', subcategory = '아이'
 WHERE category IS NULL
   AND (product_name LIKE '%섀도우%' OR product_name LIKE '%마스카라%' OR product_name LIKE '%팔레트%');

UPDATE product SET category = '헤어·바디', subcategory = '헤어'
 WHERE category IS NULL AND (product_name LIKE '%샴푸%' OR product_name LIKE '%트리트먼트%');

UPDATE product SET category = '헤어·바디', subcategory = '바디'
 WHERE category IS NULL AND product_name LIKE '%바디%';

-- 어느 규칙에도 걸리지 않은 것.
UPDATE product SET category = '기타', subcategory = '기타' WHERE category IS NULL;

ALTER TABLE product ALTER COLUMN category    SET NOT NULL;
ALTER TABLE product ALTER COLUMN subcategory SET NOT NULL;

-- 카테고리로 거르고 페이지를 자르는 조회가 기본 동선이다.
CREATE INDEX idx_product_category ON product (category, subcategory);
