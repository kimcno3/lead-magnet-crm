# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

리드마그넷 CRM 운영 시스템. 운영자가 AI로 만든 단일 HTML 폼을 등록해 캠페인·공개 폼·채널별 배포 링크를 만들고, 신청을 CRM 명단으로 모아 성과를 본다. 전체 계획과 결정 이유는 [docs/PLAN.md](docs/PLAN.md)에 있다.

## Commands

```bash
# 전체 로컬 실행 (postgres, backend :8080·:8081, admin-web :5173)
cp .env.example .env
docker compose up --build

# backend (Gradle 툴체인이 JDK 21을 자동으로 받는다. 실행 JDK는 17 이상이면 된다)
cd backend
./gradlew build                                   # 컴파일 + 테스트 (CI와 동일)
./gradlew test --tests 'com.leadmagnet.crm.HealthCheckTest'          # 테스트 클래스 하나
./gradlew test --tests 'com.leadmagnet.crm.HealthCheckTest.헬스체크는_UP을_반환한다'  # 메서드 하나
./gradlew bootRun

# admin-web
cd admin-web
npm ci
npm run dev          # :5173, /api/admin·/actuator/health를 localhost:8080으로 프록시 (BACKEND_ADMIN_URL로 변경)
npm run lint         # oxlint --deny-warnings
npm run typecheck
npm test             # vitest run (jsdom)
npx vitest run src/App.test.tsx   # 파일 하나
npm run build
```

CI(`.github/workflows/ci.yml`)는 develop·main 대상 PR과 push에서 backend `./gradlew build`, admin-web lint·typecheck·test·build를 돌린다.

## 구조

- `backend/`: Spring Boot 3.5 단일 앱. 루트 패키지 `com.leadmagnet.crm`, 그 아래 도메인 패키지.
  - 출처별 포트는 `app.ports.admin`/`app.ports.public-port`(`common/PortsProperties`, 환경 변수 `ADMIN_PORT`/`PUBLIC_PORT`). 기본 커넥터는 admin 포트에 뜬다. public 커넥터(:8081)와 OriginGuardFilter는 이슈 #7에서 붙이므로 그전에는 8081이 열려 있지 않다.
  - actuator는 `health`만 노출한다. compose 헬스체크와 admin-web이 이 경로를 쓴다.
- `admin-web/`: React + Vite + TypeScript 관리자 SPA. 운영 시에는 nginx(`admin-web/nginx.conf`), 개발 시에는 Vite가 `/api/admin/`과 `/actuator/health`를 backend admin 포트로 프록시한다. 브라우저 입장에서는 admin-web 출처 하나뿐이어서 refresh 쿠키(SameSite=Strict, Path=/api/admin/auth)가 그대로 동작하고, public 출처(:8081)와는 출처가 다르다.
- `docker-compose.yml`: postgres → backend → admin-web 순서로 헬스체크를 기다린다. 값은 루트 `.env`에서 읽는다.
- `docs/`: `PLAN.md`(과제 해석, 데이터 모델, 지표 정의, API, 이슈 순서 6.1·6.2절), `ROADMAP.md`(범위 밖), `adr/`.
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
- README에는 실행·테스트 방법만 쓴다. 다른 문서는 `docs/`에 둔다.
- 범위 밖: 실제 AI 연동, 외부 CRM·문자·알림톡·광고 연동, 범용 폼 빌더, 결제. 배포는 [docs/ROADMAP.md](docs/ROADMAP.md).

## 코드 규칙

- 백엔드 패키지는 도메인별로 나눈다: `auth, template, campaign, form, link, tracking, lead, stats, common`.
- CRUD는 JPA, 집계는 네이티브 SQL. 지표 정의가 SQL에 그대로 보이게 쓴다.
- 스키마는 Flyway로만 바꾼다(`ddl-auto`는 `validate`).
- 에러 응답은 `ProblemDetail`로 통일한다(401, 403, 404, 409, 410, 413, 422).
- 테스트는 Testcontainers의 실제 PostgreSQL로 돌린다. H2는 쓰지 않는다.
- API는 springdoc-openapi 애너테이션으로 문서화한다.
- 공개 폼 래퍼는 React와 분리된 vanilla JS로 만든다.
