-- 개발용 초기 상품 데이터
--
-- 과제 명세 532p의 콘솔 시나리오에 나오는 상품 목록을 그대로 옮겼다.
-- local 프로파일에서 ddl-auto가 스키마를 만든 뒤 실행된다.
--
-- MERGE를 쓰는 이유: INSERT면 애플리케이션을 재시작할 때마다 같은 행을 다시 넣으려 해
-- product_name 유니크 제약에 걸린다. MERGE는 있으면 갱신하고 없으면 넣는다.
--
-- KEY를 id가 아니라 product_name으로 잡은 이유: id를 직접 지정하면 자동 증가 시퀀스가
-- 시드 값을 건너뛰지 못해, 시퀀스를 수동으로 옮기는 문장이 따로 필요해진다.
-- 그 문장은 재시작마다 시퀀스를 같은 값으로 되돌리므로, 영속 DB에서는 이미 발급된 ID와
-- 충돌한다. product_name을 키로 쓰면 id를 DB가 발급해 그 문제가 아예 생기지 않는다.
MERGE INTO product (product_name, product_price) KEY (product_name) VALUES
    ('무선마우스',      15000),
    ('블루투스키보드',  29000),
    ('USB허브',        39000);
