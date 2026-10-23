-- V10: 보존 기간 파기(익명화)와 동의 문구 증빙 (ADR 0020)
-- 잠금: 백필 UPDATE는 leads 전체 행을 다시 쓰고, CHECK 추가는 테이블을 훑는 동안 ACCESS EXCLUSIVE, CREATE INDEX는 쓰기를 막는다.
-- 지금 규모(수만 행 이하)에서는 짧다. 큰 테이블이면 NOT VALID + VALIDATE, CREATE INDEX CONCURRENTLY, 배치 백필로 나눈다(ADR 0020).
-- 추가만 하는 변경이고, ck_leads_contact는 "익명화되지 않은 행만 연락처 필수"로 넓힌다(기존 행은 모두 만족한다).

-- 캠페인별 신청 보존 기간(일). 신청 시각에서 retention_days × 24시간이 지난 신청을 익명화한다.
ALTER TABLE campaigns ADD COLUMN retention_days INTEGER NOT NULL DEFAULT 365;
ALTER TABLE campaigns ADD CONSTRAINT ck_campaigns_retention_days CHECK (retention_days BETWEEN 1 AND 3650);

-- 익명화 시각(NULL = 아직 보존 중). 동의 문구 원문과 sha256(UTF-8 바이트, 소문자 hex)은 신청 시점 폼 문구의 복사본이다.
ALTER TABLE leads
    ADD COLUMN anonymized_at  TIMESTAMPTZ,
    ADD COLUMN consent_text   TEXT,
    ADD COLUMN consent_sha256 CHAR(64);

-- 백필(최선): 기존 신청은 폼의 현재 동의 문구로 채운다. 폼 문구는 draft에서만 바꿀 수 있고(ADR 0013) draft 폼은 신청을 받지
-- 않으므로 보통은 신청 당시 문구와 같지만, DB를 직접 고친 경우까지 보장하지는 못한다.
UPDATE leads l
   SET consent_text = f.consent_text,
       consent_sha256 = encode(sha256(convert_to(f.consent_text, 'UTF8')), 'hex')
  FROM forms f
 WHERE f.id = l.form_id;

-- 문구와 sha256은 함께 있거나 함께 없다. NULL은 백필 전 버전의 인스턴스가 넣은 행(롤링 배포 중)만 가능하다.
ALTER TABLE leads ADD CONSTRAINT ck_leads_consent_evidence CHECK ((consent_text IS NULL) = (consent_sha256 IS NULL));

-- 익명화한 행은 연락처 암호문·해시가 모두 NULL이다. 익명화되지 않은 행은 지금처럼 전화번호나 이메일 중 하나가 필수다.
ALTER TABLE leads DROP CONSTRAINT ck_leads_contact;
ALTER TABLE leads ADD CONSTRAINT ck_leads_contact
    CHECK (anonymized_at IS NOT NULL OR phone_enc IS NOT NULL OR email_enc IS NOT NULL);

-- 파기 대상 찾기. 파기 쿼리는 아직 익명화하지 않은 행을 id 순 keyset(id > 직전 id ORDER BY id LIMIT n)으로 읽고
-- 캠페인 보존 기간은 조인으로 비교하므로, 같은 조건의 부분 인덱스를 id로 둔다. 익명화된 행은 인덱스에서 빠진다.
CREATE INDEX ix_leads_retention_pending ON leads (id) WHERE anonymized_at IS NULL;
