-- 개발용 초기 상품 데이터
--
-- 과제 명세 532p의 콘솔 시나리오에 나오는 상품 목록을 그대로 옮겼다.
-- local 프로파일에서 ddl-auto가 스키마를 만든 뒤 실행된다.
--
-- MERGE를 쓰는 이유: INSERT면 애플리케이션을 재시작할 때마다 같은 행을 다시 넣으려 해
-- product_name 유니크 제약에 걸린다. MERGE는 있으면 갱신하고 없으면 넣는다.
MERGE INTO product (id, product_name, product_price) KEY (id) VALUES
    (1, '무선마우스',    15000),
    (2, '블루투스키보드', 29000),
    (3, 'USB허브',      39000);

-- 시드가 1~3번을 쓰므로, 이후 자동 생성 ID가 4번부터 나오도록 시퀀스를 옮긴다.
-- 이 줄이 없으면 새 상품 등록 시 ID 1이 다시 발급되어 기본키 충돌이 난다.
ALTER TABLE product ALTER COLUMN id RESTART WITH 4;
