# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

리드마그넷 CRM 운영 시스템. 운영자가 AI로 만든 단일 HTML 폼을 등록해 캠페인·공개 폼·채널별 배포 링크를 만들고, 신청을 CRM 명단으로 모아 성과를 본다. 전체 계획과 결정 이유는 [docs/PLAN.md](docs/PLAN.md)에 있다.

## Commands

```bash
# 전체 로컬 실행 (postgres, backend :8080·:8081, admin-web :5173)
# .env.example을 복사하면서 JWT_SECRET, 개인정보 암호화 키 두 개, 폼 토큰 키를 새로 만든다(자리표시 값으로는 기동하지 않는다)
sed -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" \
    -e "s|^LEAD_ENC_KEY=.*|LEAD_ENC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^LEAD_HMAC_KEY=.*|LEAD_HMAC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^FORM_TOKEN_KEY=.*|FORM_TOKEN_KEY=$(openssl rand -base64 32)|" .env.example > .env
docker compose up --build

# backend (Gradle 툴체인이 JDK 21을 자동으로 받는다. 실행 JDK는 17 이상이면 된다)
cd backend
./gradlew build                                   # 컴파일 + 테스트 (CI와 동일)
./gradlew test --tests 'com.leadmagnet.crm.HealthCheckTest'          # 테스트 클래스 하나
./gradlew test --tests 'com.leadmagnet.crm.HealthCheckTest.헬스체크는_UP을_반환한다'  # 메서드 하나
./gradlew bootRun
./gradlew test --tests '*OpenApiExportTest'           # docs/api/openapi.yaml 재생성(앱이 내보낸 /v3/api-docs.yaml). API를 바꾸면 커밋에 포함
./gradlew test -Dopenapi.verify=true --tests '*OpenApiExportTest'   # 커밋된 파일이 최신인지만 확인(파일을 쓰지 않음)

# admin-web
cd admin-web
npm ci
npm run dev          # :5173, /api/admin·/actuator/health를 localhost:8080으로 프록시 (BACKEND_ADMIN_URL로 변경, 공개 폼 주소는 VITE_PUBLIC_BASE_URL)
npm run lint         # oxlint --deny-warnings
npm run typecheck
npm test             # vitest run (jsdom)
npx vitest run src/App.test.tsx   # 파일 하나
npm run build
```

CI(`.github/workflows/ci.yml`)는 develop·main 대상 PR과 push에서 backend `./gradlew build -Dopenapi.verify=true`(커밋된 openapi.yaml이 최신인지도 확인), admin-web lint·typecheck·test·build를 돌린다. `.github/workflows/e2e.yml`은 같은 조건에서 `docker compose up --build -d --wait` 뒤 `e2e`의 Playwright를 돌린다(결과는 `E2E_OUTPUT_DIR`=`e2e/ci-output`, 아티팩트만, 커밋하지 않음).

```bash
# 브라우저 E2E (docker compose up --build -d 뒤)
cd e2e && npm ci && npx playwright install chromium && npm test   # 결과: docs/e2e (E2E_OUTPUT_DIR로 변경)
```

## 구조

- `backend/`: Spring Boot 3.5 단일 앱. 루트 패키지 `com.leadmagnet.crm`, 그 아래 도메인 패키지.
  - 출처별 포트는 `app.ports.admin`/`app.ports.public-port`(`common/PortsProperties`, 환경 변수 `ADMIN_PORT`/`PUBLIC_PORT`). 기본 커넥터는 admin 포트, public 커넥터는 `common/web/PublicPort`가 붙인다(ADR 0010). 실제 public 포트는 `PublicPort.get()`으로 읽는다(테스트는 임의 포트).
  - `common/web`: `OriginGuardFilter`(Security보다 앞, public 포트는 `PublicPaths`만, admin 포트는 그 밖만, 나머지 404)와 `PublicSecurityHeadersFilter`(public 포트 응답 전부에 CSP 등, ADR 0002). 공개 경로를 늘리려면 `PublicPaths`와 ADR 0010을 함께 바꾼다.
  - actuator는 `health`만 노출한다. compose 헬스체크와 admin-web이 이 경로를 쓴다.
  - 프로필: `application.yml`(공통, `ddl-auto: validate`, 기본 프로필 `local`)과 `application-local.yml`(`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`).
  - 스키마는 `src/main/resources/db/migration/V*.sql`(Flyway)이 유일한 원천이다. 상태·채널 값과 링크·채널 일관성은 CHECK 제약으로 DB에서도 막는다.
  - 도메인 패키지는 `domain`(엔티티·리포지토리), `application`(유스케이스 서비스), `api`(컨트롤러·DTO), `config`(보안·웹 설정)로 나눈다.
  - 인증(ADR 0003): `auth/config/SecurityConfig`에 필터 체인 세 개(admin `/api/admin/**` Bearer JWT, public `/f/**`·`/l/**`·`/api/public/**` permitAll·인증 헤더 무시, 기본 체인은 헬스·문서 외 denyAll). 토큰 발급·교체는 `auth/application/AuthService`, 키·CORS·쿠키 설정은 `AuthProperties`(`JWT_SECRET` 필수, `ADMIN_ORIGIN`, `COOKIE_SECURE`). 현재 시각은 `Clock` 빈(`common/ClockConfig`)으로 얻는다.
  - API 문서: 컨트롤러의 springdoc 애너테이션과 `common/OpenApiConfig`(서버·설명, 공개 경로는 오퍼레이션 단위로 :8081을 가리킴)가 만든다. 문서 엔드포인트는 admin 포트에서만 열리고 공개 컨트롤러도 같은 문서에 들어간다. `docs/api/openapi.yaml`은 `docs/OpenApiExportTest`가 쓴다(손으로 고치지 않는다).
  - 에러는 `common/error`의 `ErrorCode`·`DomainException`을 던지면 `GlobalExceptionHandler`가 ProblemDetail(`code` 포함)로 바꾼다. Security 401·403도 같은 형식이다.
  - 캠페인·폼(ADR 0013): `campaign`과 `form` 애그리거트는 서로 id로만 참조한다. 폼의 `fieldConfig`는 생성 시 템플릿 `fields`를 복사한 스냅샷이며 `label`·`required`만 draft에서 수정할 수 있다. 상태 값은 DB CHECK에 맞춰 소문자로 저장하므로(`*StatusConverter`) enum에 `@Enumerated`를 쓰지 않는다.
  - 배포 링크(ADR 0006): `link` 패키지. 폼당 채널별 링크 하나는 DB 유니크가 아니라 `LinkService`가 지킨다. `/l/{code}`는 방문을 기록하지 않고 `/f/{slug}?lm={code}`로 302만 하며(방문자 쿠키만 발급), 채널 귀속은 `LinkService.attribute`/`resolveForForm`으로 한다. URL 앞부분은 `PUBLIC_BASE_URL`(`app.public-base-url`).
  - 크리에이터(ADR 0014): `creator` 패키지(등록·수정·보관). 링크의 `creator_id`(NULL = 자체 링크)로 귀속하며 방문·신청의 크리에이터는 `link_id`로 유도한다(스키마에 없음). 폼 × 채널 × 크리에이터(없음 포함)당 링크 하나도 `LinkService`가 지키고, 보관된 크리에이터로는 새 링크를 만들 수 없다. 크리에이터별 지표(`StatsQuery`)는 `COALESCE(creator_id, 0)`을 키로 쓰며 0은 "직접·자체 링크"(자체 링크 + direct), 전환은 같은 크리에이터 안의 교집합이다.
  - 공개 폼(ADR 0002): `/f/{slug}`는 동적 값이 없는 고정 셸(`form/api/WrapperPages`)이고, `static/f/_assets/form-wrapper.js`가 `/api/public/forms/{slug}`로 받은 HTML을 sandbox iframe srcdoc에 넣는다. 셸에 서버 값을 끼워 넣지 않는다(CSP에 `'unsafe-inline'`이 있다).
  - 방문(`tracking`): `/f/{slug}` 200마다 `VisitRecorder`가 한 행을 남긴다. 방문자 id는 `tracking/api/VisitorIdCookie`(`lm_vid`, UUID v4가 아니면 새로 발급).
  - 신청(ADR 0007, 0008): `lead` 패키지. 제출 `POST /api/public/forms/{slug}/submissions`는 서버가 신뢰 경계다(sandbox HTML이 CSP `connect-src 'self'`로 직접 부를 수 있다): JSON만 받고, `Origin: null`은 403 `forbidden_origin`, 동의와 fieldConfig 검증은 `lead/domain/SubmissionValidator`가 한다(오류는 `errors[{field,message,code}]`로 모아 422). 중복(같은 폼의 같은 phone/email)은 V4 해시 부분 유니크 인덱스(`(form_id, phone_hash)`, `(form_id, email_hash)`; V2 평문 인덱스는 V5에서 삭제)와 `INSERT ... ON CONFLICT DO NOTHING`으로 409 `duplicate_submission`; 유니크 위반 예외를 쓰지 않는 이유는 DB 오류 로그에 전화번호 원문이 실리기 때문이다. 방문자 쿠키는 `VisitorIdCookie.read`로 읽기만 하고 제출에서는 발급하지 않는다.
  - 개인정보 암호화(ADR 0015, 평문 컬럼은 V5에서 삭제): `common/crypto`의 `PiiCipher`(AES-256-GCM, `version|iv|ciphertext+tag`)·`PiiHasher`(HMAC-SHA256 hex, 전화번호는 숫자만·이메일은 소문자로 정규화) 키는 `PiiCryptoProperties`(`LEAD_ENC_KEY`·`LEAD_HMAC_KEY`, base64 32바이트, 없거나 틀리면 기동 실패). 신청은 `*_enc`·`*_hash`만 쓰고 중복은 해시 부분 유니크 인덱스가 대상 없는 `ON CONFLICT DO NOTHING`으로 거른다. 암·복호화는 `lead/application/ContactProtector`에서만 하고, 명단은 가린 값(`PiiMasker.maskName`·`maskPhoneForDisplay`·`maskEmailForDisplay`), 원문은 `LeadContactService.reveal`(`GET /api/admin/leads/{id}/contact`)로만 준다. V5는 맨 앞 `DO` 가드로 백필 안 된 행이 있으면 중단한다. 웹 서버가 요청을 받기 전에(`SmartInitializingSingleton`, Flyway 이후) `common/crypto/PiiKeyCheck`가 `pii_key_check` 표식 행으로 현재 키가 기존 데이터의 키와 같은지 확인하고 다르면 기동을 멈춘다. 암호문은 컬럼 이름을 AAD로 묶는다(`PiiCipher.encrypt(value, column)`). 명단에서 복호화하지 못한 행은 `contactUnavailable`로 비우고 원문 API는 500으로 실패한다.
  - 감사 로그(ADR 0016): `audit` 패키지(V6 `audit_logs`, 쓰기·조회만). 쓰기는 `audit/application/AuditService`로만 한다. 원문을 내주는 유스케이스(`LeadContactService.reveal`, `LeadExportService.start`)는 `recordRequired`(호출자 트랜잭션, 실패하면 요청도 실패), 명단 조회·로그인은 유스케이스 트랜잭션이 끝난 뒤 컨트롤러가 `recordBestEffort`(트랜잭션 안에서 부르면 예외, 실패하면 WARN만). 기록 모양은 `AuditEvent` 정적 팩토리. 요청 주체는 `audit/api/AuditActors`가 만드는 `audit/domain/AuditActor`(운영자 = JWT `sub`, IP = `getRemoteAddr()`, 헤더는 직접 읽지 않음). 의존은 auth·lead·campaign → audit 한 방향이고 운영자 이메일은 audit의 포트 `OperatorDirectory`를 auth가 구현한다. `detail`에 신청자 원문을 넣지 않는다. 명단 CSV(`GET /api/admin/campaigns/{id}/leads.csv`)는 기록·범위(`id <= maxId`) 확정 뒤 keyset으로 스트리밍하고(`spring.mvc.async.request-timeout` 10분), 칸은 `common/Csv`(RFC 4180, 앞 공백 뒤 수식 시작 문자에 `'`)로 만든다.
  - 게시 스냅샷(ADR 0018): `Form.publish`가 템플릿 `content`·`sha256`을 `forms.published_html`·`published_template_sha256`(V8)에 복사하고, 공개 API는 그것만 쓴다(`PublicFormService`는 `TemplateService`를 쓰지 않는다). 템플릿 새 버전은 `PUT /api/admin/templates/{id}`(`form/api/TemplateVersionController` → `form/application/TemplateVersionService`, template 패키지는 form을 모른다)이고 draft 폼의 `field_config`만 다시 스냅샷한다. 게시·폼 수정과 새 버전 업로드는 템플릿 행 → 폼 행 순서로 `PESSIMISTIC_WRITE`를 잡는다. 이 순서를 어기면 교착이나 html·필드 불일치가 생긴다.
  - 업로드 검사(ADR 0017): `template/domain/TemplateInspector`(Jsoup, 순수)가 계약 오류와 별개로 경고 `[{code, severity, message, location}]`를 낸다. 업로드를 막지 않고, `TemplateService.inspect`(저장 없음)를 업로드·`POST /api/admin/templates/inspect`·새 버전 업로드가 함께 쓴다. `html_templates.inspection`(V7)이 NULL인 기존 행은 `inspectionOf`가 읽을 때 계산하고 저장하지 않는다. 경고 코드를 바꾸면 `docs/AI_FORM_PROMPT.md` 대응표·TROUBLESHOOTING 3.1절·`admin-web/src/aiFormPrompt.ts`를 함께 맞춘다(테스트가 확인).
  - 지표(ADR 0005): `stats`는 엔티티 없이 `StatsQuery`(NamedParameterJdbcTemplate 네이티브 SQL, 지표 정의를 SQL 주석으로 인용)가 집계한다. 기간은 `common/DateRange`가 Asia/Seoul 날짜 `[from 00:00, to+1 00:00)`로 바꾸고 형식 오류·`from > to`는 422. 전환 방문자는 방문∩신청 `visitor_id` 교집합(채널별은 같은 채널 안), `visitor_id` NULL 신청은 전환에 넣지 않는다.
  - 데이터 품질(ADR 0019): 봇 UA 방문은 `visits.is_bot`(V9, `tracking/domain/BotUserAgents`, 패턴은 `app.tracking.bot-user-agents`)으로 남기고 `StatsQuery`의 모든 방문 CTE가 `NOT v.is_bot`으로 뺀다. 제출은 `SubmissionGuardFilter`(403 → 413 → 429 rate limit, `common/web/RateLimiter` IP·`lm_vid` 버킷) → 404·410 → `FormTokenService.verify`(작성 시간 토큰 `ft`, `FORM_TOKEN_KEY`, 2초~24시간, 422 `invalid_form_token`) → honeypot `fields._lm_trap`(201, 저장 안 함, 밑줄 시작이라 템플릿 필드 이름과 겹치지 않음) → 필드 검증 순서다. 이 순서를 바꾸지 않는다. 테스트 제출은 `support/FormTokens.mature`로 토큰을 받는다(시계를 되돌려 받으므로 sleep 없음). `support/PublicOrigin`은 브라우저 User-Agent를 기본으로 싣는다(HTTP 라이브러리 UA는 봇 방문).
  - 클라이언트 IP·로그인 시도 제한(ADR 0021): `common/web/ClientIpFilter`(가장 먼저 도는 필터)가 admin 포트에서 직전 피어가 `TRUSTED_PROXIES`(`app.trusted-proxies`, CIDR, 기본 compose의 admin-web 고정 주소 `172.16.57.10/32`; 게이트웨이 `.1`이 들어가는 대역 전체는 믿지 않는다. compose는 :8080·:5432를 루프백에만 연다)일 때만 X-Forwarded-For를 오른쪽부터 읽어 `getRemoteAddr()`를 바꾼다. public 포트는 XFF·X-Real-IP를 절대 읽지 않는다. IP가 필요한 곳은 모두 `getRemoteAddr()`만 쓴다. 로그인은 `auth/application/LoginThrottle`(인메모리 `common/web/SlidingWindowLimiter`, (이메일, IP) 쌍 5회·이메일 전체 50회·IP 20회/15분 실패, Retry-After는 걸린 한도 중 가장 긴 대기, 시도 전에 자리를 잡고 성공·중단이면 돌려줌)이 막고 429 `too_many_login_attempts` + Retry-After(`TooManyRequestsException`). 없는 이메일도 같은 규칙, 잠금 시작만 감사 `auth.login.locked` 한 줄(IP 범위만이면 이메일 없음), 거절은 `RejectionTally` 1분 건수 로그. 테스트는 한도를 크게·루프백을 신뢰 프록시로 두고(`@IntegrationTest`), 한도 자체는 `LoginThrottleApiTest`가 따로 띄운다.
  - 보존 기간 파기(ADR 0020): `campaigns.retention_days`(1~3650, 기본 365)가 지난 신청을 `Lead.anonymize`가 익명화한다(암호문·해시 NULL, payload는 키만, `anonymized_at`; 행·지표용 값·동의 문구는 남김). `lead/application/LeadRetentionService`가 배치마다 `pg_try_advisory_xact_lock`을 잡고 keyset으로 처리하며(못 잡으면 그 실행은 멈추고 `interrupted`), 실행마다 감사 `retention.anonymize` 한 줄(배치가 실패해도 부분 건수·`completed=false`를 먼저 기록한 뒤 예외). 보존 기간 변경은 `campaign.retention.update`. 스케줄링은 `common/SchedulingConfig`가 켜고, 파기 스케줄러(`lead/config/RetentionScheduler`, 매일 03:00 KST)만 `app.retention.enabled`로 켜고 끈다(테스트는 끔, 배치 2건). 수동 `POST /api/admin/retention/run[?dryRun=true]`. 파기된 신청은 원문 API 410 `lead_anonymized`, CSV 마지막 열 "파기". 동의 증빙(`consent_text`·`consent_sha256`, `lead/domain/ConsentEvidence`)은 신청 시 폼 문구를 복사하고 `GET /api/admin/leads/{id}/consent`(감사 기록 없음)로 본다. 테스트에서 경계 시각은 `MutableClock.freezeAt`.
  - 운영자 계정은 기동 시 `auth/application/OperatorSeeder`가 `ADMIN_SEED_EMAIL`/`ADMIN_SEED_PASSWORD`로 만든다(ADR 0009). 로그용 개인정보 마스킹은 `common/PiiMasker`.
  - 통합 테스트는 `@IntegrationTest`(`support/`)를 붙인다. Testcontainers `postgres:17-alpine`을 컨텍스트 캐시로 공유하므로 각 테스트 전에 `DatabaseCleaner.truncateAll`로 비운다. 새 테이블을 만들면 여기에 추가한다. 단 `pii_key_check`는 일부러 넣지 않는다: 업무 데이터가 아니라 컨텍스트 기동 때 한 번 쓰는 키 확인 행이고, 공유 컨텍스트에서 지우면 다음 확인이 다른 키로 행을 새로 만들 수 있어 키 불일치 검사를 무력화한다(`PiiKeyCheckTest`는 행을 지운 뒤 `@AfterEach`에서 원래 키로 되돌린다). `RestAssured.port`는 admin 포트로 두고 public 포트 요청은 `PublicOrigin.given(publicPort)`로 보낸다. Docker가 떠 있어야 backend 테스트가 돈다.
  - 시나리오 테스트(`scenario` 테스트 패키지): `HappyPath`·`Failure`·`Isolation`이 두 포트를 오가며 로그인→업로드→배포→방문→신청→지표를 실제 HTTP로 잇는다. 기능별 테스트를 복제하지 않고 앞 단계 출력이 다음 입력이 되는지만 본다. 공통 도우미는 `support/ScenarioBase`, 방문자 쿠키를 들고 `/l/{code}` 302를 직접 따라가는 `support/PublicVisitor`. 지표는 테스트 주석에 손계산 표를 두고 정확히 비교한다.
- `admin-web/`: React + Vite + TypeScript 관리자 SPA. 운영 시에는 nginx(`admin-web/nginx.conf`), 개발 시에는 Vite가 `/api/admin/`과 `/actuator/health`를 backend admin 포트로 프록시한다. 브라우저 입장에서는 admin-web 출처 하나뿐이어서 refresh 쿠키(SameSite=Strict, Path=/api/admin/auth)가 그대로 동작하고, public 출처(:8081)와는 출처가 다르다.
  - 관리자 UI(ADR 0011): `src/api/client.ts`가 access token을 모듈 메모리에만 두고 401 → refresh 한 번(동시 401은 공유) → 재시도를 맡는다. refresh의 네트워크 오류는 세션 종료(401)와 구분해 토큰을 지우지 않는다. 도메인별 호출은 `src/api/*.ts`, DTO 타입은 `src/api/types.ts`(백엔드 record와 같은 이름). 세션 복원·로그인 상태는 `src/auth/`(`AuthProvider`, `RequireAuth`).
  - 화면은 `src/pages/`(로그인, 캠페인 목록·상세 + `campaign/` 탭 세 개, 크리에이터 목록·상세, 템플릿 목록·상세, 감사 로그), 공용 컴포넌트는 `src/components/`, 포맷(Asia/Seoul·ko-KR)은 `src/format.ts`, 화면 문구·코드별 안내는 `src/labels.ts`. 스타일은 `src/styles/`의 CSS 변수 토큰(라이브러리 없음).
  - 업로드 HTML 미리보기는 `components/SandboxPreview.tsx` 하나로만 렌더링한다. CSP 값은 `admin-web/csp.ts`가 원천이고 nginx.conf와 Vite dev 서버가 같은 값을 쓴다(`csp.test.ts`가 일치 확인, dev만 Fast Refresh nonce 추가).
  - 캠페인 상세의 탭·기간·명단 필터·페이지·펼친 폼은 URL 쿼리(`tab`, `from`, `to`, `form`, `channel`, `page`, `open`)에 둔다.
  - 공개 폼 URL 앞부분은 링크 응답 `url`에서 얻고 없으면 빌드 시 `VITE_PUBLIC_BASE_URL`(compose가 `PUBLIC_BASE_URL`을 빌드 인자로 넘김).
  - 파일 다운로드(명단 CSV)는 토큰을 링크에 실을 수 없어 `client.ts`의 `apiDownload`(401 refresh 포함) → `saveDownload`(Blob, object URL)로 받는다.
  - 화면 테스트는 `src/test/http.ts`의 `mockBackend`(경로별 가짜 fetch, refresh·me 기본 응답 포함)와 `src/test/render.tsx`의 `renderPage`로 쓴다.
- `docker-compose.yml`: postgres → backend → admin-web 순서로 헬스체크를 기다린다. 값은 루트 `.env`에서 읽는다.
- `docs/`: `PLAN.md`(과제 해석, 데이터 모델, 지표 정의, API, 이슈 순서 6.1·6.2절), `OVERVIEW.md`(제출 개요), `api/openapi.yaml`, `ROADMAP.md`(범위 밖), `adr/`.
- `scripts/bootstrap-github.sh`: 라벨·마일스톤·이슈를 gh api로 만든다. 여러 번 실행해도 안전하다.

## 보안 불변식 (위반 금지)

변경이 필요해 보이면 코드를 고치기 전에 저장소 소유자에게 먼저 묻는다. 변경에는 ADR과 승인이 필요하다.

- :8080 admin 출처는 `/api/admin/**`와 관리자 화면만, :8081 public 출처는 `/f/**`, `/l/**`, `/api/public/**`만 제공한다.
  - OriginGuardFilter가 맞지 않는 포트로 들어온 요청을 404로 차단한다.
  - 관리자 API의 CORS는 admin 출처만 허용한다.
- 업로드 HTML은 `<iframe sandbox="allow-scripts allow-forms" srcdoc>`로만 렌더링한다. `allow-same-origin`은 금지다.
  - 제출은 postMessage로 래퍼에 넘기고, 래퍼가 검증한 뒤 public API로 POST한다.
  - 공개 응답에는 CSP를 붙인다.
- access token은 응답 바디로 주고 프론트 메모리에만 둔다. localStorage·sessionStorage는 금지다.
  - refresh token은 HttpOnly, SameSite=Strict, Path=/api/admin/auth 쿠키로 주고, 사용할 때마다 교체하며 재사용을 감지하면 토큰 패밀리 전체를 폐기한다.
- 개인정보(전화번호, 이메일)는 로그에 원문으로 남기지 않는다.
- 시크릿은 커밋하지 않는다. 새 환경 변수는 `.env.example`에 추가한다.
- 보안 관련 변경에는 실패 케이스 테스트를 함께 넣는다.

## 작업 규칙

- 이슈 하나 = 브랜치 하나 = PR 하나. 브랜치 이름은 `feat|fix|refactor|test|docs|chore/{이슈번호}-{요약}`.
- PR은 develop을 대상으로 squash 병합한다. main에는 release PR로 병합하고 `vX.Y.Z` 태그를 단다.
- 커밋과 PR 제목은 Conventional Commits(`feat(auth): refresh token 교체 적용`). 내용은 한국어로 써도 된다.
- PR 템플릿 체크리스트(마이그레이션 하위 호환, 보안 영향, ADR, OpenAPI, .env.example)를 채운다.
- Flyway: 이미 적용된 파일은 수정하지 않는다. PR당 마이그레이션은 최대 하나. 파괴적 변경은 expand → contract.
- 각 PR에는 해당 기능의 성공·실패 테스트와 관련 ADR을 함께 넣는다. 모호한 요구는 가정을 세워 ADR에 기록한다.
- README에는 실행·테스트 방법과 `docs/` 문서 목록만 둔다. 사용 가이드(`docs/GUIDE.md`), 문제 해결(`docs/TROUBLESHOOTING.md`), 진행 현황(`docs/WORKPLAN.md`)은 기능을 바꿀 때 함께 갱신한다.
- 범위 밖: 실제 AI 연동, 외부 CRM·문자·알림톡·광고 연동, 범용 폼 빌더, 결제. 배포는 [docs/ROADMAP.md](docs/ROADMAP.md).

## 코드 규칙

- 백엔드 패키지는 도메인별로 나눈다: `auth, template, campaign, form, link, tracking, lead, stats, audit, creator, common`.
- CRUD는 JPA, 집계는 네이티브 SQL. 지표 정의가 SQL에 그대로 보이게 쓴다.
- 스키마는 Flyway로만 바꾼다(`ddl-auto`는 `validate`).
- 에러 응답은 `ProblemDetail`로 통일한다(401, 403, 404, 409, 410, 413, 422, 429).
- 테스트는 Testcontainers의 실제 PostgreSQL로 돌린다. H2는 쓰지 않는다.
- API는 springdoc-openapi 애너테이션으로 문서화한다.
- 공개 폼 래퍼는 React와 분리된 vanilla JS로 만든다.
