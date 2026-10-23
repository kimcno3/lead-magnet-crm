# 리드마그넷 CRM 운영 시스템 — 계획

## 1. 배경

글로우업리즈(크리에이터 기반 커머스·강의·매니지먼트 회사, R/S 성과형 협업 구조)의 개발자 채용 과제 "리드마그넷 CRM 운영 시스템"을 3일 안에 구현해서 제출한다.

과제 요약은 다음과 같다.
- 운영자가 AI로 만든 단일 .html 폼 파일을 등록하고, 그 HTML로 캠페인과 커스텀 신청 폼을 만든다.
- 인스타그램, X, 유튜브, 스레드용 배포 링크를 만든다.
- 방문자는 공개 폼에서 신청하고, 신청 데이터는 DB에 CRM 명단으로 저장한다.
- 운영자는 캠페인별 방문, 방문자, 신청, 전환율과 채널별 성과를 본다.
- 비기능 요구사항
  - 인증한 운영자만 관리자 기능과 신청자 데이터에 접근할 수 있다.
  - 등록한 HTML은 관리자 인증 정보와 관리자 API에 접근할 수 없어야 한다.
  - 핵심 성공·실패 흐름을 자동화 테스트로 검증한다.
  - 인증, 폼 관리, 공개 폼 제출, 배포 링크, 성과 조회 API를 문서화한다.
  - README에는 실행 방법과 테스트 방법만 쓴다.
  - 주요 결정은 ADR로 남긴다. 모호한 부분은 합리적인 가정을 세워 ADR에 기록한다.
  - 관계형 DB를 쓴다.
- 제출물: 소스 코드, DB 스키마와 마이그레이션, .env.example, 테스트 코드, API 문서, README, ADR
- 범위 밖: 실제 AI 연동, 외부 CRM·문자·알림톡·광고 연동, 범용 폼 빌더, 결제·계약·상담사 관리

설계 원칙은 세 가지다.
1. 운영자가 무엇을 올려도 시스템이 안전해야 한다. 보안은 운영자의 주의가 아니라 구조로 보장한다.
2. CRM이므로 보안을 기능이 아니라 체계로 다룬다. 인증, 출처 격리, 개인정보의 수명 주기를 일관되게 설계하고 테스트로 증명한다.
3. AI로 기능을 만드는 것보다 무엇을 만들지 판단하는 데 집중한다. 모호한 지점은 근거와 함께 결정해서 기록하고, 추가 기능은 회사의 사업 구조에 연결되는 것만 고른다.

## 2. 확정된 결정과 이유

| 선택 | 이유 | 쓰지 않은 대안 |
|---|---|---|
| Java 21, Spring Boot 3.x, Gradle Kotlin DSL | 가장 익숙한 스택이고, 보안·트랜잭션·검증 생태계가 표준화되어 있다 | Node |
| Spring Security, FilterChain 두 개 | 관리자와 공개 영역을 구조적으로 분리한다 | 직접 만든 인터셉터 |
| OAuth2 Resource Server + 자체 발급 JWT | 외부 인증 솔루션(Keycloak, Auth0)에 종속되지 않고 토큰 검증은 Spring 표준에 맡긴다. 이후 Authorization Server를 도입해도 Resource Server는 그대로 쓴다 | password grant(OAuth 2.1에서 폐기), 세션 쿠키 |
| PostgreSQL | 관계형 DB 요구, jsonb, COUNT DISTINCT, 파티셔닝 지원 | MySQL |
| JPA(CRUD) + 네이티브 SQL(집계) | 지표 정의가 코드에 그대로 보인다 | 전부 JPQL |
| Flyway | 마이그레이션 SQL이 그대로 스키마 제출물이 된다 | ddl-auto |
| Jsoup | HTML 파싱, 필드 추출, 정적 검사 | 정규식 |
| springdoc-openapi | 코드와 문서가 어긋나지 않는다 | 수기 문서 |
| JUnit 5, Testcontainers, RestAssured | 실제 Postgres로 검증한다. H2는 쓰지 않는다 | H2 |
| 단일 앱 + 포트 두 개 | 출처 단위로 격리하면서 3일 안에 끝낼 수 있다 | Gradle 멀티모듈(로드맵) |
| sandbox iframe + postMessage | AI가 만든 HTML의 표현력은 살리고 권한은 0으로 만든다 | sanitize |
| React + Vite + TypeScript (admin-web) | 메모리 토큰 SPA 구조와 맞는다. 공개 폼 래퍼는 별도 vanilla JS | Thymeleaf |
| Docker Compose 로컬 실행 | 명령 하나로 재현할 수 있다. 실제 배포는 로드맵으로 미룬다 | — |
| main(prod) + develop(dev) 브랜치 | 브랜치와 환경이 1:1로 대응한다 | Trunk 방식과 태그 배포 |

## 3. 보안 불변식

위반 금지. 변경하려면 ADR과 저장소 소유자 승인이 필요하다.

- :8080 admin 출처는 /api/admin/** 와 관리자 화면만, :8081 public 출처는 /f/**, /l/**, /api/public/** 만 제공한다.
  - OriginGuardFilter가 맞지 않는 포트로 들어온 요청을 404로 차단한다.
  - 관리자 API의 CORS는 admin 출처만 허용한다.
- 업로드 HTML은 `<iframe sandbox="allow-scripts allow-forms" srcdoc>`로만 렌더링한다. allow-same-origin은 금지다.
  - 제출은 postMessage로 래퍼에 넘기고, 래퍼가 검증한 뒤 public API로 POST한다.
  - 공개 응답에는 CSP를 붙인다.
- access token은 응답 바디로 주고 프론트 메모리에만 둔다. localStorage·sessionStorage는 금지다.
  - refresh token은 HttpOnly, SameSite=Strict, Path=/api/admin/auth 쿠키로 주고, 사용할 때마다 교체하며 재사용을 감지하면 토큰 패밀리 전체를 폐기한다.
- 개인정보(전화번호, 이메일)는 로그에 원문으로 남기지 않는다. 시크릿은 커밋하지 않고, 새 환경 변수는 .env.example에 추가한다.
- 보안 관련 변경에는 실패 케이스 테스트를 함께 넣는다.

## 4. 설계

### 데이터 모델 (초안)

- operators: id, email(unique), password_hash, created_at
- refresh_tokens: id, operator_id, token_hash, family_id, expires_at, revoked_at
- html_templates: id, name, content, sha256, fields(jsonb), inspection(jsonb, 업로드 검사 경고, ADR 0017), size, version(새 버전마다 +1), created_by, created_at, updated_at (ADR 0018)
- campaigns: id, name, description, status, starts_at, ends_at, retention_days(신청 보존 기간 일 수, 1~3650, 기본 365, ADR 0020)
- forms: id, campaign_id, template_id, slug(unique), title, field_config(jsonb), consent_text, status(draft|published|closed), published_html·published_template_sha256(게시 시점 템플릿 스냅샷, ADR 0018)
- creators: id, name, handle, memo, status(active|archived), created_by, created_at, updated_at (ADR 0014)
- distribution_links: id, form_id, creator_id(nullable), channel(instagram|x|youtube|threads), code(unique), utm_*
- visits: id, form_id, link_id, channel, visitor_id, referrer, user_agent, is_bot(봇 UA 방문, 지표에서 제외, ADR 0019), occurred_at
- leads: id, form_id, campaign_id, link_id, channel, visitor_id, name_enc·phone_enc·email_enc(AES-GCM), phone_hash·email_hash(중복 판정용 HMAC), payload(jsonb), consent_at, consent_text·consent_sha256(신청 시점 동의 문구 증빙, ADR 0020), anonymized_at(보존 기간 파기 시각, ADR 0020), created_at (평문 연락처 컬럼은 없다, ADR 0015)
- audit_logs: id, operator_id, action, target_type, target_id, campaign_id, detail(jsonb), ip, user_agent, created_at (명단 열람·다운로드 기록, ADR 0016)
- login_failures: id, key_hash(HMAC, 이메일·IP 원문 없음), failed_at (로그인 시도 제한의 공유 저장소, 15분 창이 지난 행은 정리, ADR 0021)
- pii_key_check: id(1만), enc_check, hmac_check (현재 키가 기존 데이터의 키와 같은지 기동 때 확인하는 표식 행, ADR 0015·0022)

### 지표 정의 (ADR로 확정할 가정)

| 지표 | 정의 |
|---|---|
| 방문 | visits 행 수 (페이지 로드 1회 = 1건) |
| 방문자 | COUNT(DISTINCT visitor_id). visitor_id는 public 출처의 1st-party 익명 쿠키 |
| 신청 | leads 행 수 |
| 전환율 | 신청한 고유 방문자 수 / 방문자 수. 분모가 0이면 0 |
| 채널별 성과 | 위 지표를 channel별로 집계. 채널 없이 들어온 방문은 direct |

### 채널 귀속

`/l/{code}`로 들어오면 visit을 기록하고 `/f/{slug}?lm={code}`로 302 리다이렉트한다. 직접 접근한 방문은 direct로 기록한다.

### API

| 영역 | 엔드포인트 |
|---|---|
| 인증 | POST /api/admin/auth/login, POST /api/admin/auth/refresh, POST /api/admin/auth/logout, GET /api/admin/auth/me |
| 템플릿 | POST·GET /api/admin/templates, GET /api/admin/templates/{id} |
| 캠페인·폼 | POST·GET·PATCH /api/admin/campaigns, POST /api/admin/campaigns/{id}/forms, PATCH /api/admin/forms/{id} |
| 배포 링크 | POST·GET /api/admin/forms/{id}/links |
| 명단 | GET /api/admin/campaigns/{id}/leads |
| 성과 | GET /api/admin/campaigns/{id}/stats, GET /api/admin/campaigns/{id}/stats/channels, GET /api/admin/stats/campaigns |
| 공개 | GET /l/{code}, GET /f/{slug}, GET /api/public/forms/{slug}, POST /api/public/forms/{slug}/submissions |

에러 응답은 ProblemDetail로 통일한다(401, 403, 404, 409, 410, 413, 422).

백엔드 패키지는 도메인별로 나눈다: auth, template, campaign, form, link, tracking, lead, stats, common.

## 5. 작업 규칙

- 이슈 하나 = 브랜치 하나 = PR 하나.
  - 브랜치 이름: feat|fix|refactor|test|docs|chore/{이슈번호}-{요약}
  - develop으로 squash 병합하고, release PR로 main에 병합하며 vX.Y.Z 태그를 단다.
- 커밋과 PR 제목은 Conventional Commits를 따른다(예: `feat(auth): refresh token 교체 적용`). 내용은 한국어로 써도 된다.
- PR 템플릿 체크리스트
  - 마이그레이션 하위 호환 여부
  - 보안 영향
  - ADR 필요 여부
  - OpenAPI 갱신
  - .env.example 갱신
- Flyway
  - 이미 적용된 파일은 수정하지 않는다.
  - PR당 마이그레이션은 최대 하나만 넣는다.
  - 파괴적 변경은 expand → contract 순서로 나눈다.
- 라벨
  - 종류: type:feat, type:fix, type:chore, type:docs, type:test
  - 보안 영향: security
  - 범위: scope:core, scope:extra
  - 영역: area:*
- 마일스톤: v0.1.0 core, v0.2.0 extras, v0.3.0 hardening, v0.4.0 operations
- 프로필: local(사용 중), dev와 prod(로드맵)
- README에는 실행·테스트 방법만 쓴다. 다른 문서는 docs/에 둔다.

## 6. 작업 계획

### 6.1 v0.1.0 core (PR 순서)

1. chore: 저장소 골격과 로컬 Docker 실행 환경. backend, admin-web, docker-compose, .env.example, GitHub Actions CI
2. feat(db): Flyway V1 스키마, 프로필 분리, 운영자 시드(환경 변수)
3. feat(auth): JWT 발급, refresh 교체·재사용 감지, Resource Server 설정, 실패 테스트
4. feat(template): HTML 업로드(단일 .html, 크기·MIME 검증), Jsoup 필드 추출
5. feat(campaign): 캠페인 CRUD, 템플릿 기반 폼 생성, 게시·마감
6. feat(link): 4개 채널 링크 생성, /l/{code} 리다이렉트
7. feat(public): 8081 커넥터, OriginGuardFilter, 샌드박스 래퍼, visitor 쿠키, 방문 추적, 격리 테스트
8. feat(lead): 신청 제출 검증, 채널 귀속, 명단 조회, 중복 신청 처리
9. feat(stats): 캠페인·채널 지표, 기간 필터
10. test: 로그인부터 지표 숫자 검증까지 이어지는 성공 시나리오와 실패 시나리오 묶음
11. feat(admin-web): 로그인, 업로드·미리보기, 캠페인·폼·링크, 대시보드, 명단
12. docs: ADR 전체, docs/OVERVIEW.md, docs/api/openapi.yaml, README 확정, v0.1.0 릴리스

### 테스트 전략

- **성공 흐름**: 로그인 → 업로드 → 캠페인 → 폼 게시 → 링크 생성 → 방문 → 신청 → 지표 숫자 검증
- **실패 흐름**
  - 미인증, 잘못된 비밀번호, 만료 토큰, refresh 재사용
  - 비HTML 파일, 용량 초과, 빈 파일
  - 필수 필드 누락, 형식 오류
  - 게시 전·마감 폼, 존재하지 않는 code
  - 중복 신청, 허용되지 않은 채널
- **격리 검증**
  - public 포트로 /api/admin 접근 시 차단
  - 공개 출처에서 관리자 API로 CORS 요청 시 거부
  - CSP 헤더와 iframe sandbox 속성 존재

### 6.2 v0.2.0 extras (core 완료 후, 우선순위 순)

1. 크리에이터별 성과 추적: creators 테이블, 링크를 크리에이터 × 채널 단위로 생성, 크리에이터별 지표
2. 개인정보 암호화(AES-GCM)와 HMAC 해시, 조회 시 마스킹, 원문 열람 API 분리
3. 명단 열람·다운로드 감사 로그
4. 업로드 HTML 검사 리포트(폼 계약 체크, 외부 전송·외부 스크립트 경고)와 운영자용 AI 프롬프트 가이드
5. 게시 시 템플릿 스냅샷
6. 데이터 품질 방어: honeypot, rate limit, 봇 방문 제외
7. 보존기한 파기(익명화)와 동의 문구 버전 기록

### 6.3 v0.3.0 hardening (v0.2.0 최종 리뷰의 후속)

구현 계획은 [plans/v0.3.0-hardening.md](plans/v0.3.0-hardening.md), 진행은 [WORKPLAN](WORKPLAN.md)에 있다.

1. 로그인 시도 제한과 신뢰 프록시 뒤 실제 IP ([ADR 0021](adr/0021-login-throttle-and-trusted-proxy.md))
2. `payload`의 연락처 모양 추가 항목 경고와 마스킹
3. 감사 로그 공백 메우기
4. Playwright E2E를 CI에 통합
5. 크리에이터별 `utm_content`(V12)와 패키지 의존 방향 정리

### 6.4 v0.4.0 operations (v0.3.0 최종 리뷰의 후속)

구현 계획은 [plans/v0.4.0-operations.md](plans/v0.4.0-operations.md), 진행은 [WORKPLAN](WORKPLAN.md)에 있다. 배포는 계정과 비용 결정이 필요해 이번 범위에서 뺐다([ROADMAP](ROADMAP.md)).

1. 감사 액션 목록의 OpenAPI 생성과 새 감사 행의 IP 검증 (#65)
2. 로그인 시도 제한의 공유 저장소(`login_failures`, V13)와 `/refresh` 제한 ([ADR 0021](adr/0021-login-throttle-and-trusted-proxy.md) 변경 기록, #66)
3. 개인정보 키 교체: 암호문 형식 2, 재암호화 API, HMAC 키 교체 ([ADR 0022](adr/0022-pii-key-rotation.md), #67)

### 로드맵

이번 범위 밖의 작업은 [ROADMAP.md](ROADMAP.md)에 둔다.

### ADR 목록

0001 기술 스택, 0002 HTML 격리, 0003 인증, 0004 HTML 폼 계약, 0005 지표 정의, 0006 채널 귀속, 0007 중복 신청 정책, 0008 신청 데이터 저장 구조, 0009 운영자 시드, 0010 단일 앱·포트 분리, 0011 관리자 UI, 0012 브랜치·환경 전략, 0013 캠페인·폼 상태 모델, 0014 크리에이터 귀속, 0015 개인정보 암호화, 0016 감사 로그와 명단 CSV 다운로드, 0017 업로드 HTML 검사 리포트, 0018 게시 시 템플릿 스냅샷과 새 버전 업로드, 0019 데이터 품질 방어, 0020 보존 기간 파기와 동의 문구 증빙, 0021 로그인 시도 제한과 신뢰 프록시 뒤 실제 IP, 0022 개인정보 키 교체

### 결과 설명 초안 (docs/OVERVIEW.md와 제출 메일용)

- 이 시스템을 크리에이터 트래픽을 리드로 바꾸고, 그 리드가 어느 채널과 크리에이터에서 왔는지 증명하는 내부 매출 인프라로 이해했다.
- CRM이기에 보안을 구조로 다뤘다. 출처와 FilterChain으로 영역을 분리했고, 업로드 HTML은 sandbox로 권한을 0으로 만들었다. 인증은 외부 솔루션에 종속되지 않도록 Spring Security의 OAuth 2.0 표준 컴포넌트 위에 직접 구성했다. 이 보안 요구들은 모두 테스트로 증명한다.
- AI로 기능을 만드는 것보다 무엇을 만들지 판단하는 데 집중했다. 모호한 요구는 ADR로 결정을 남겼고, 추가 기능은 회사의 사업 구조에 연결되는 것만 골랐다. 저장소 규칙과 CLAUDE.md로 AI가 정해진 규칙 안에서 작업하도록 통제했다.
