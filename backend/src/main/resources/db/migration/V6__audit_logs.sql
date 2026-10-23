-- V6: 감사 로그 (ADR 0016)
-- 명단 조회, 원문 열람, CSV 다운로드, 로그인 성공·실패를 남긴다. 추가만 하는 변경이고 애플리케이션은 쓰기만 한다(수정·삭제 API 없음).
-- operator_id는 로그인 실패처럼 운영자를 특정하지 않는 기록에서 NULL이다. 기록이 대상보다 오래 남도록 외래 키를 두지 않는다.
-- detail에는 필터·페이지·건수·가린 이메일만 둔다. 신청자 원문 개인정보는 넣지 않는다.

CREATE TABLE audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    operator_id BIGINT,
    action      VARCHAR(40)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   BIGINT,
    campaign_id BIGINT,
    detail      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip          VARCHAR(45),
    user_agent  VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX ix_audit_logs_operator_created_at ON audit_logs (operator_id, created_at);
CREATE INDEX ix_audit_logs_campaign_created_at ON audit_logs (campaign_id, created_at);
