-- V1: 리드마그넷 CRM 초기 스키마
-- 시각은 모두 timestamptz(UTC 저장). 상태·채널 값은 CHECK 제약으로 DB에서도 막는다.

-- 운영자 -------------------------------------------------------------------
CREATE TABLE operators (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_operators_email_lower CHECK (email = lower(email))
);
CREATE UNIQUE INDEX ux_operators_email ON operators (email);

-- refresh token: 원문은 저장하지 않고 SHA-256 해시만 둔다. 같은 로그인에서 이어진 토큰은 family_id를 공유한다.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    operator_id BIGINT      NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_operator_id ON refresh_tokens (operator_id);

-- 업로드 HTML 템플릿 ----------------------------------------------------------
CREATE TABLE html_templates (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    fields     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    size       INTEGER      NOT NULL CHECK (size > 0),
    created_by BIGINT       NOT NULL REFERENCES operators (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 캠페인 -------------------------------------------------------------------
CREATE TABLE campaigns (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    created_by  BIGINT       NOT NULL REFERENCES operators (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_campaigns_status CHECK (status IN ('active', 'archived')),
    CONSTRAINT ck_campaigns_period CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at)
);

-- 폼: 캠페인 + 템플릿으로 만든 공개 신청 폼 --------------------------------------
CREATE TABLE forms (
    id           BIGSERIAL    PRIMARY KEY,
    campaign_id  BIGINT       NOT NULL REFERENCES campaigns (id),
    template_id  BIGINT       NOT NULL REFERENCES html_templates (id),
    slug         VARCHAR(64)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    field_config JSONB        NOT NULL DEFAULT '[]'::jsonb,
    consent_text TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'draft',
    published_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_forms_status CHECK (status IN ('draft', 'published', 'closed')),
    CONSTRAINT ck_forms_slug CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$')
);
CREATE UNIQUE INDEX ux_forms_slug ON forms (slug);
CREATE INDEX ix_forms_campaign_id ON forms (campaign_id);

-- 채널별 배포 링크 -------------------------------------------------------------
CREATE TABLE distribution_links (
    id           BIGSERIAL    PRIMARY KEY,
    form_id      BIGINT       NOT NULL REFERENCES forms (id),
    channel      VARCHAR(20)  NOT NULL,
    code         VARCHAR(16)  NOT NULL,
    utm_source   VARCHAR(100),
    utm_medium   VARCHAR(100),
    utm_campaign VARCHAR(100),
    utm_content  VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_distribution_links_channel CHECK (channel IN ('instagram', 'x', 'youtube', 'threads'))
);
CREATE UNIQUE INDEX ux_distribution_links_code ON distribution_links (code);
CREATE INDEX ix_distribution_links_form_id ON distribution_links (form_id);

-- 방문: 공개 폼 페이지 로드 1회 = 1행. 링크 없이 들어오면 channel = 'direct' -----------------
CREATE TABLE visits (
    id          BIGSERIAL   PRIMARY KEY,
    form_id     BIGINT      NOT NULL REFERENCES forms (id),
    link_id     BIGINT      REFERENCES distribution_links (id),
    channel     VARCHAR(20) NOT NULL,
    visitor_id  UUID        NOT NULL,
    referrer    TEXT,
    user_agent  TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_visits_channel CHECK (channel IN ('instagram', 'x', 'youtube', 'threads', 'direct')),
    CONSTRAINT ck_visits_link_channel CHECK ((link_id IS NULL) = (channel = 'direct'))
);
CREATE INDEX ix_visits_form_occurred ON visits (form_id, occurred_at);
CREATE INDEX ix_visits_form_channel ON visits (form_id, channel);

-- 신청(CRM 명단). 연락처 핵심 필드는 컬럼으로, 나머지 폼 입력은 payload로 둔다 (ADR 0008) ------
CREATE TABLE leads (
    id          BIGSERIAL    PRIMARY KEY,
    form_id     BIGINT       NOT NULL REFERENCES forms (id),
    campaign_id BIGINT       NOT NULL REFERENCES campaigns (id),
    link_id     BIGINT       REFERENCES distribution_links (id),
    channel     VARCHAR(20)  NOT NULL,
    visitor_id  UUID,
    name        VARCHAR(100),
    phone       VARCHAR(20),
    email       VARCHAR(254),
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    consent_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_leads_channel CHECK (channel IN ('instagram', 'x', 'youtube', 'threads', 'direct')),
    CONSTRAINT ck_leads_link_channel CHECK ((link_id IS NULL) = (channel = 'direct')),
    CONSTRAINT ck_leads_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE INDEX ix_leads_campaign_created ON leads (campaign_id, created_at);
CREATE INDEX ix_leads_form_created ON leads (form_id, created_at);
CREATE INDEX ix_leads_form_channel ON leads (form_id, channel);
