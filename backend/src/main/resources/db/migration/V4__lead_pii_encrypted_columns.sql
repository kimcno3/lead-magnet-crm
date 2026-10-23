-- 개인정보 암호화 expand 단계 (ADR 0015). 평문 name·phone·email은 그대로 두고 암호문과 중복 판정용 해시를 더한다.
-- *_enc: AES-256-GCM, 형식 version(1B)|iv(12B)|ciphertext+tag. *_hash: HMAC-SHA256(LEAD_HMAC_KEY, 정규화한 값) hex.
-- 기존 행은 애플리케이션 기동 시 PiiBackfillRunner가 채운다(키가 DB 밖에 있어 SQL로는 채울 수 없다).
-- 평문 컬럼·평문 유니크 인덱스 삭제와 ck_leads_contact 교체는 contract 단계(V5)에서 한다.
-- 이 파일은 병합 전 리뷰에서 바뀌었다(pii_key_check 추가). 공유 환경에는 적용된 적이 없다. 이전 초안을 적용한 로컬 DB는
-- Flyway 체크섬이 맞지 않아 기동하지 못하므로 docker compose down -v로 지우고 다시 띄운다.
ALTER TABLE leads
    ADD COLUMN name_enc   BYTEA,
    ADD COLUMN phone_enc  BYTEA,
    ADD COLUMN email_enc  BYTEA,
    ADD COLUMN phone_hash CHAR(64),
    ADD COLUMN email_hash CHAR(64);

-- 중복 신청 정책 (ADR 0007)을 해시로도 보장한다. INSERT ... ON CONFLICT DO NOTHING은 대상을 지정하지 않아
-- 평문 인덱스(V2)와 이 해시 인덱스 모두에서 충돌을 거른다.
CREATE UNIQUE INDEX ux_leads_form_phone_hash ON leads (form_id, phone_hash) WHERE phone_hash IS NOT NULL;
CREATE UNIQUE INDEX ux_leads_form_email_hash ON leads (form_id, email_hash) WHERE email_hash IS NOT NULL;

-- 키 확인 행 (ADR 0015). 처음 기동할 때 정해진 표식 값을 그때의 키로 암호화·HMAC해 한 행만 둔다.
-- 다음 기동부터 PiiKeyCheck가 현재 키로 이 행을 확인하고, 키가 바뀌었으면 기동을 멈춘다(섞인 키로 데이터를 쓰지 않게).
CREATE TABLE pii_key_check (
    id         SMALLINT    PRIMARY KEY CHECK (id = 1),
    enc_check  BYTEA       NOT NULL,
    hmac_check CHAR(64)    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
