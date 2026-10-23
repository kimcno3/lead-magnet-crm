-- 개인정보 암호화 contract 단계 (ADR 0015). 평문 name·phone·email과 평문 유니크 인덱스를 지우고
-- ck_leads_contact를 암호문 기준으로 바꾼다.
-- 가드: 평문은 있는데 암호문·해시가 없는 행(14a 백필이 끝나지 않은 행)이 하나라도 있으면 마이그레이션을 멈춘다.
-- 이 상태로 평문 컬럼을 지우면 그 행의 연락처가 사라진다. 14a 버전으로 기동해 백필을 마친 뒤 다시 시도한다.
DO $$
BEGIN
    IF (SELECT count(*) FROM leads
        WHERE (name IS NOT NULL AND name_enc IS NULL)
           OR (phone IS NOT NULL AND (phone_enc IS NULL OR phone_hash IS NULL))
           OR (email IS NOT NULL AND (email_enc IS NULL OR email_hash IS NULL))) > 0 THEN
        RAISE EXCEPTION 'V5 중단: 암호문·해시가 채워지지 않은 leads 행이 있습니다. 14a 버전으로 기동해 백필을 마친 뒤 다시 시도하세요 (ADR 0015)';
    END IF;
END $$;

DROP INDEX ux_leads_form_phone;
DROP INDEX ux_leads_form_email;

ALTER TABLE leads DROP CONSTRAINT ck_leads_contact;
ALTER TABLE leads
    DROP COLUMN name,
    DROP COLUMN phone,
    DROP COLUMN email,
    ADD CONSTRAINT ck_leads_contact CHECK (phone_enc IS NOT NULL OR email_enc IS NOT NULL);
