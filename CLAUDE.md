# CLAUDE.md

리드마그넷 CRM 운영 시스템. 운영자가 AI로 만든 단일 HTML 폼을 등록해 캠페인·공개 폼·채널별 배포 링크를 만들고, 신청을 CRM 명단으로 모아 성과를 본다. 전체 계획과 결정 이유는 [docs/PLAN.md](docs/PLAN.md)에 있다.

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
