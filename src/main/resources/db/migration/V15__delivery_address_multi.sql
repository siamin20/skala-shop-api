-- V15: 배송지 여러 개와 공동현관 비밀번호
--
-- ── 공동현관 비밀번호 ──
--
-- 아파트·오피스텔은 현관을 못 열면 배송이 그 자리에서 멈춘다. 기사가 전화하고
-- 사람이 받지 못하면 반송된다. 실제 커머스가 이 칸을 따로 두는 이유다. (D34)
--
-- 민감한 값이다. 이걸 알면 건물에 들어갈 수 있다. 그래서 두 가지를 지킨다.
--   1. 응답에 값을 싣지 않는다 (등록 여부만 알려준다)
--   2. 로그에 남기지 않는다
ALTER TABLE delivery_address ADD COLUMN entrance_password VARCHAR(50);

-- ── 배송지 별칭 ──
--
-- 여러 개를 두면 목록에서 구분할 이름이 필요하다. "집", "회사"처럼.
-- 주소 앞부분으로 구분하게 두면 같은 아파트의 다른 동을 구별하지 못한다.
ALTER TABLE delivery_address ADD COLUMN label VARCHAR(30);

UPDATE delivery_address SET label = '기본 배송지' WHERE label IS NULL;
ALTER TABLE delivery_address ALTER COLUMN label SET NOT NULL;

-- ── 기본 배송지가 하나인 것은 애플리케이션이 지킨다 ──
--
-- 처음에는 부분 유니크 인덱스를 걸려고 했다.
--
--   CREATE UNIQUE INDEX ... ON delivery_address (customer_id) WHERE is_default = TRUE;
--
-- PostgreSQL은 되지만 **H2가 WHERE 절이 붙은 인덱스를 지원하지 않는다.**
-- 개발 프로파일이 기동조차 못 해서 뺐다. D21에서 겪은 것과 같은 종류의 차이다.
--
-- 두 DB 모두에서 되는 대안은 마땅치 않다. (customer_id, is_default) 유니크는
-- 기본이 아닌 배송지를 하나로 제한해버려 목적과 반대다.
--
-- 그래서 DeliveryAddressService가 새 기본을 지정하기 전에 기존 기본을 먼저 내린다.
-- 최종 방어선이 없는 셈이라, 동시에 두 요청이 들어오면 기본이 둘이 될 수 있다.
-- 배송지 설정은 한 사람이 자기 화면에서 바꾸는 동작이라 그 위험을 감수했다.
