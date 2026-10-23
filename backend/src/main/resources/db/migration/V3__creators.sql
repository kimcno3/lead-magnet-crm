-- V3: 크리에이터와 크리에이터별 배포 링크 (ADR 0014)
-- 추가만 하는 변경이다(기존 링크의 creator_id는 NULL = 크리에이터 없음). 방문·신청 스키마는 바꾸지 않고
-- 크리에이터는 link_id → distribution_links.creator_id로 유도한다.

CREATE TABLE creators (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    handle     VARCHAR(100),
    memo       TEXT,
    status     VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_by BIGINT       NOT NULL REFERENCES operators (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_creators_status CHECK (status IN ('active', 'archived'))
);

-- 폼 × 채널 × 크리에이터(없음 포함)당 링크 하나는 애플리케이션(LinkService)이 지킨다(ADR 0006·0014).
ALTER TABLE distribution_links ADD COLUMN creator_id BIGINT REFERENCES creators (id);
CREATE INDEX ix_distribution_links_creator_id ON distribution_links (creator_id);
