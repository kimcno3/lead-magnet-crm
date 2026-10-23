-- V7: 업로드 HTML 검사 결과 (ADR 0017)
-- 업로드 시점의 경고 목록 [{code, severity, message, location}]을 저장한다. 추가만 하는 변경이다.
-- NULL은 "아직 계산하지 않음"이다. 이 마이그레이션 이전에 올린 템플릿은 SQL로 HTML을 검사할 수 없어 NULL로 두고,
-- 애플리케이션이 읽을 때 계산한다(저장하지 않는다).

ALTER TABLE html_templates ADD COLUMN inspection JSONB;
