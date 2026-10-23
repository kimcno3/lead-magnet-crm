-- V8: 게시 시점 템플릿 스냅샷과 템플릿 새 버전 (ADR 0018)
-- 추가만 하는 변경이다. 폼은 게시할 때 템플릿 원문과 sha256을 자기 행에 복사하고, 공개 폼은 그것만 쓴다.

ALTER TABLE forms ADD COLUMN published_html TEXT;
ALTER TABLE forms ADD COLUMN published_template_sha256 CHAR(64);

-- 이미 게시(또는 마감)된 폼은 지금 템플릿 내용을 스냅샷으로 삼는다. draft는 게시할 때 채운다.
UPDATE forms f
   SET published_html = t.content,
       published_template_sha256 = t.sha256
  FROM html_templates t
 WHERE t.id = f.template_id
   AND f.status <> 'draft';

-- draft가 아니면 스냅샷이 있어야 한다. 백필 뒤라 기존 행은 모두 만족한다.
ALTER TABLE forms ADD CONSTRAINT ck_forms_published_snapshot
    CHECK (status = 'draft' OR (published_html IS NOT NULL AND published_template_sha256 IS NOT NULL));

-- 템플릿 새 버전: 업로드할 때마다 1씩 오르는 번호와 마지막 변경 시각
ALTER TABLE html_templates ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE html_templates ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE html_templates SET updated_at = created_at;
