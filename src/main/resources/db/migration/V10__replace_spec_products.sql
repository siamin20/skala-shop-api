-- V10: 명세 예시 상품을 뷰티 상품으로 교체
--
-- V2가 넣은 무선마우스·블루투스키보드·USB허브는 명세 532p 콘솔 시나리오의 상품이다. (D29)
-- 주제를 뷰티 커머스로 정한 뒤에도 목록에 남아 있어 화면에서 튀었다.
--
-- 지우지 않고 이름과 가격만 바꾼다. 두 가지 이유다.
--   1. 행을 지우면 order_item이 참조하고 있을 때 외래 키에 걸린다
--   2. id가 유지되어 기존 주문 내역이 끊기지 않는다
--
-- V2를 수정하지 않는 것도 같은 원칙이다. 이미 적용된 마이그레이션을 고치면
-- Flyway 체크섬이 어긋나 기존 DB가 기동하지 못한다.
UPDATE product SET product_name = '수분 진정 토너패드 70매', product_price = 18000
 WHERE product_name = '무선마우스';

UPDATE product SET product_name = '콜라겐 탄력 나이트 크림 50ml', product_price = 34000
 WHERE product_name = '블루투스키보드';

UPDATE product SET product_name = '딥클렌징 오일 200ml', product_price = 26000
 WHERE product_name = 'USB허브';
