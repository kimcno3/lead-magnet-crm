-- V13: 로그인 시도 제한의 공유 저장소 (ADR 0021 변경 기록, v0.4.0)
-- 한 행 = 한 키에 잡은 자리 하나(시도 전에 잡고, 성공·중단이면 id로 지운다). 시도 하나는 세 키((이메일, IP) 쌍, 이메일, IP)에
-- 한 행씩 남긴다. key_hash는 HMAC-SHA256(LEAD_HMAC_KEY, "login-throttle:{범위}:{값}") hex 64자이며 이메일·IP 원문은 저장하지 않는다.
-- 창(기본 15분)이 지난 행은 정리 스케줄러가 지운다. 업무 데이터가 아니므로 다른 테이블을 참조하지 않는다.
CREATE TABLE login_failures (
    id        BIGSERIAL PRIMARY KEY,
    key_hash  CHAR(64)    NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_failures_key_failed_at ON login_failures (key_hash, failed_at);
-- 정리(failed_at <= 기준 시각)가 전체를 훑지 않게 한다
CREATE INDEX idx_login_failures_failed_at ON login_failures (failed_at);
