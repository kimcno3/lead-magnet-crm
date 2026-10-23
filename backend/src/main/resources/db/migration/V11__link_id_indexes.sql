-- V11: visits.link_id, leads.link_id 인덱스 (v0.2.0 최종 리뷰 반영)
-- 크리에이터별 지표(StatsQuery)와 명단의 크리에이터 이름 조회가 link_id로 조인한다. FK에는 인덱스가 자동으로 생기지 않는다.
-- 추가만 하는 변경이다.
-- 일반 CREATE INDEX는 만드는 동안 해당 테이블의 쓰기를 막는다. 이 프로젝트의 규모에서는 순간이지만,
-- 행이 많이 쌓인 운영 DB에서는 이 파일 대신 트랜잭션 밖에서 CREATE INDEX CONCURRENTLY로 먼저 만들어 두고
-- (Flyway의 executeInTransaction=false 설정 파일 또는 수동 실행), 이 마이그레이션은 IF NOT EXISTS 덕분에 건너뛴다.

CREATE INDEX IF NOT EXISTS ix_visits_link_id ON visits (link_id);
CREATE INDEX IF NOT EXISTS ix_leads_link_id ON leads (link_id);
