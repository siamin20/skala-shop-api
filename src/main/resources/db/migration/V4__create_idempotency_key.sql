-- V4: 멱등성 키
--
-- 클라이언트가 타임아웃 후 같은 요청을 재시도하면 포인트가 두 번 차감되거나
-- 두 번 충전된다. 요청 내용만으로는 재시도와 새 요청을 구분할 수 없기 때문이다. (D20)
--
-- 요청마다 붙는 일회용 키를 저장해 두면 "이미 처리한 요청"임을 알 수 있다.
-- 교재 613p의 "Consumer는 이벤트 ID로 중복을 판단하고 무시"와 같은 원리를 API 계층에 적용한 것이다.
CREATE TABLE idempotency_key (
    -- 클라이언트가 보낸 키. 기본 키로 두면 유니크 제약이 곧 동시 요청 방어선이 된다.
    -- 애플리케이션 검사만으로는 두 요청이 동시에 "없음"을 확인하고 둘 다 실행할 수 있다.
    idempotency_key      VARCHAR(100) PRIMARY KEY,

    -- 키 소유자. 남이 쓴 키를 재사용해 그 응답을 훔쳐보는 것을 막는다.
    customer_id          VARCHAR(50)  NOT NULL,

    -- 요청 내용의 지문. 같은 키로 다른 요청을 보내면 거부한다.
    -- 이게 없으면 키를 재사용해 "5,000원 충전"의 응답으로 "50,000원 충전"을 가장할 수 있다.
    request_fingerprint  VARCHAR(64)  NOT NULL,

    -- 최초 처리 결과. 재시도에는 이 값을 그대로 돌려주고 작업은 실행하지 않는다.
    response_body        CLOB         NOT NULL,

    -- 만료 시각. 지나면 새 요청으로 취급한다.
    -- 만료 행을 지우는 배치는 두지 않았다. 조회 시 검사하므로 동작은 정확하고,
    -- 테이블이 쌓이는 것은 운영 문제라 P5에서 다룬다. (D20)
    expires_at           TIMESTAMP    NOT NULL,

    CONSTRAINT fk_idempotency_key_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
);

-- 만료 검사가 모든 조회에 붙으므로 인덱스를 둔다.
CREATE INDEX idx_idempotency_key_expires ON idempotency_key (expires_at);
