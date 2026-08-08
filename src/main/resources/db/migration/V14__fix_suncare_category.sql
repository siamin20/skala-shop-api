-- V14: 선크림 분류 교정
--
-- V12의 규칙이 순서대로 적용되면서 "무기자차 선크림"이 크림·로션으로 들어갔다.
-- "선크림"에 "크림"이 들어 있어 앞선 규칙에 먼저 걸린 것이다. (D35)
--
-- 문자열 규칙으로 분류할 때 흔한 함정이다. 더 구체적인 규칙(선크림)을
-- 일반적인 규칙(크림)보다 먼저 적용했어야 했다.
UPDATE product SET category = '클렌징·선케어', subcategory = '선케어'
 WHERE product_name LIKE '%선크림%' OR product_name LIKE '%SPF%';
