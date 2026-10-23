-- 중복 신청 정책 (ADR 0007): 같은 폼에 같은 전화번호 또는 같은 이메일은 한 번만 접수한다.
-- 애플리케이션 검사만으로는 동시 제출을 막을 수 없어 DB가 최종으로 보장한다. NULL은 중복으로 보지 않는다.
-- 전화번호는 숫자만, 이메일은 소문자로 정규화해 저장하므로 값 그대로 유니크하면 된다.
CREATE UNIQUE INDEX ux_leads_form_phone ON leads (form_id, phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX ux_leads_form_email ON leads (form_id, email) WHERE email IS NOT NULL;
