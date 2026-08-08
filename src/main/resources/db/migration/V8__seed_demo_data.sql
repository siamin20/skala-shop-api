-- V8: 시연용 데이터 — 뷰티 커머스
--
-- 주제를 PC 주변기기에서 화장품으로 바꾼다. (D29)
--
-- 기존 상품 3개는 명세 532p 콘솔 시나리오에서 온 것이다. 강사가 "쇼핑몰이 아닌
-- 자유주제도 가능"이라고 열어줬으므로 주제를 정할 수 있는데, 뷰티 커머스를 고른 이유가 있다.
--
--   1. 선착순 이벤트가 자연스럽다. 한정 수량 특가는 이 업계의 실제 판매 방식이다.
--      PC 부품에 "10개 한정"을 붙이면 억지스럽다
--   2. 가격대가 넓다. 만 원대 립밤부터 십만 원대 세럼까지 있어
--      잔액 부족과 소액 다건 주문을 한 화면에서 시연할 수 있다
--   3. 재구매가 잦은 품목이라 "같은 상품 재주문 시 수량 누적"(명세 529p)이 말이 된다
--
-- 명세 시나리오의 상품 3개(V2)는 지우지 않는다. 이미 적용된 마이그레이션의 결과를
-- 뒤에서 지우면 "V2가 무엇을 했는가"와 실제 상태가 어긋나 추적이 어려워진다.
-- 대신 화면에서는 신규 상품이 먼저 보이도록 정렬한다.

-- ─────────────────────────── 상품 ───────────────────────────
--
-- 가격대를 일부러 흩어 놓는다. 전부 비슷하면 잔액 부족(INSUFFICIENT_POINT)을 보이려고
-- 수십 개를 담아야 한다. 고가 상품이 하나 있으면 한 번에 보여줄 수 있다.
--
-- 재고도 흩어 놓는다. 재고 3개짜리가 있어야 품절(OUT_OF_STOCK)을 몇 번의 클릭으로 만든다.
INSERT INTO product (product_name, product_price, product_stock) VALUES
    -- 스킨케어
    ('세라마이드 수분 크림 50ml',      28000, 120),
    ('비타민C 브라이트닝 세럼 30ml',   42000,  80),
    ('저자극 약산성 클렌징폼 150ml',   14000, 200),
    ('히알루론산 토너 300ml',          19500, 150),
    ('레티놀 0.3 나이트 앰플 20ml',    58000,  45),
    ('시카 진정 수딩젤 100ml',         12000, 180),

    -- 선케어 · 마스크
    ('무기자차 선크림 SPF50+ 50ml',    23000, 160),
    ('데일리 수분 마스크팩 10매',        9900, 300),

    -- 메이크업
    ('벨벳 매트 립스틱 #로즈브릭',      21000,  90),
    ('글로우 쿠션 파운데이션 21호',     36000,  70),
    ('데일리 아이섀도우 팔레트 9색',    32000,  60),
    ('롱래시 마스카라 볼륨',           18000, 110),

    -- 헤어 · 바디
    ('아미노산 damage 케어 샴푸 500ml', 24000, 140),
    ('시어버터 바디로션 400ml',        16500, 130),

    -- 고가 라인 — 잔액 부족 시연용
    ('프리미엄 리페어 아이크림 15ml',   89000,  30),
    ('한정판 골드 앰플 세트',          320000,   8);

-- ─────────────────────── 선착순 이벤트 ───────────────────────
--
-- 수량을 작게 잡는다. 50개짜리는 화면에서 소진되는 것을 보여주기 어렵다.
-- 10개면 몇 번 눌러 품절까지 도달할 수 있다.
--
-- 기간은 CURRENT_TIMESTAMP 기준 상대 시각으로 잡는다. 고정 날짜를 쓰면
-- 마이그레이션이 언제 적용되느냐에 따라 이미 끝난 이벤트가 된다.
INSERT INTO flash_sale (sale_name, product_id, total_quantity, remaining, starts_at, ends_at)
SELECT '한정판 골드 앰플 8개 한정', p.id, 8, 8,
       CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP + INTERVAL '30' DAY
FROM product p WHERE p.product_name = '한정판 골드 앰플 세트';

INSERT INTO flash_sale (sale_name, product_id, total_quantity, remaining, starts_at, ends_at)
SELECT '아이크림 타임특가 10개', p.id, 10, 10,
       CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP + INTERVAL '30' DAY
FROM product p WHERE p.product_name = '프리미엄 리페어 아이크림 15ml';

INSERT INTO flash_sale (sale_name, product_id, total_quantity, remaining, starts_at, ends_at)
SELECT '레티놀 앰플 선착순 15개', p.id, 15, 15,
       CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP + INTERVAL '30' DAY
FROM product p WHERE p.product_name = '레티놀 0.3 나이트 앰플 20ml';

-- 이미 끝난 이벤트도 하나 둔다. 화면이 "종료됨"을 어떻게 보여주는지,
-- 참여를 시도하면 어떻게 거절하는지 시연할 대상이 필요하다.
INSERT INTO flash_sale (sale_name, product_id, total_quantity, remaining, starts_at, ends_at)
SELECT '지난주 마스크팩 특가 (종료)', p.id, 20, 0,
       CURRENT_TIMESTAMP - INTERVAL '8' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY
FROM product p WHERE p.product_name = '데일리 수분 마스크팩 10매';
