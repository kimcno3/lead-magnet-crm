-- V12: 크리에이터 링크의 utm_content (ADR 0014 변경 노트)
-- 추가만 하는 변경이다(컬럼은 V1에 이미 있다). 크리에이터 링크는 utm_content = 'creator-{creator_id}'를 갖고,
-- 자체 링크(creator_id NULL)는 NULL로 둔다. 이미 값이 있는 행은 건드리지 않는다.
UPDATE distribution_links
   SET utm_content = 'creator-' || creator_id
 WHERE creator_id IS NOT NULL
   AND utm_content IS NULL;
