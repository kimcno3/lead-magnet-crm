-- V9: 봇 방문 표시 (ADR 0019, ADR 0005 변경 기록)
-- 추가만 하는 변경이다. User-Agent가 봇 패턴이면 true로 기록하고 지표 SQL은 NOT is_bot인 방문만 센다.
-- 기존 행은 기본값 false(사람 방문으로 본다)로 채워진다. 과거 방문을 다시 판정하지 않는다.

ALTER TABLE visits ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT false;
