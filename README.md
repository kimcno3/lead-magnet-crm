# 리드마그넷 CRM 운영 시스템

## 실행 방법

필요한 것: Docker (Compose v2)

```bash
# .env.example을 복사하면서 JWT_SECRET, 개인정보 암호화 키 두 개, 폼 토큰 키를 새로 만든다(자리표시 값으로는 기동하지 않는다)
sed -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" \
    -e "s|^LEAD_ENC_KEY=.*|LEAD_ENC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^LEAD_HMAC_KEY=.*|LEAD_HMAC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^FORM_TOKEN_KEY=.*|FORM_TOKEN_KEY=$(openssl rand -base64 32)|" .env.example > .env
docker compose up --build
```

| 주소 | 내용 |
|---|---|
| http://localhost:5173 | 관리자 화면 (admin-web). 계정은 `.env`의 `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` |
| http://localhost:8080/actuator/health | backend 헬스체크 (admin 출처) |
| http://localhost:8080/swagger-ui.html | API 문서 (admin 출처) |
| http://localhost:8081/f/{slug} | 공개 폼 출처 (/f/{slug}, /l/{code}). 폼은 관리자 화면에서 게시한 뒤 열린다 |

종료: `docker compose down` (DB 데이터까지 지우려면 `docker compose down -v`)

## 테스트 방법

필요한 것: JDK 17 이상(빌드 시 JDK 21을 자동으로 받는다), Node.js 24, 실행 중인 Docker(backend 테스트가 Testcontainers로 PostgreSQL을 띄운다)

```bash
# backend
cd backend
./gradlew test

# admin-web
cd admin-web
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

브라우저 E2E(Playwright, Chromium)는 `docker compose up --build -d`로 전체 시스템을 띄운 뒤 `cd e2e && npm ci && npx playwright install chromium && npm test`로 돌린다. 화면은 `docs/e2e/screenshots/`, 단계별 결과는 `docs/e2e/results.json`에 남는다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | 사용 가이드: 화면별 사용법, AI로 신청 폼 HTML 만들 때의 조건, 유스케이스 |
| [docs/AI_FORM_PROMPT.md](docs/AI_FORM_PROMPT.md) | AI에게 신청 폼 HTML을 만들게 할 때 붙여 넣는 프롬프트, 규칙별 검사 코드 대응표, 통과하는 예시 |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | 문제 해결: 실행·로그인·업로드·신청·지표 증상별 원인과 해결, 오류 코드표 |
| [docs/WORKPLAN.md](docs/WORKPLAN.md) | 작업 계획과 진행 현황, 다음 작업 |
| [docs/RELEASE_NOTES_v0.2.0.md](docs/RELEASE_NOTES_v0.2.0.md) | v0.2.0 릴리스 노트: 기능, v0.1.0에서 올리는 방법, 필수 키 |
| [docs/OVERVIEW.md](docs/OVERVIEW.md) | 설계 개요: 아키텍처, 보안 설계, 테스트 전략 |
| [docs/PLAN.md](docs/PLAN.md) | 과제 해석과 초기 계획 |
| [docs/adr/](docs/adr/README.md) | 결정 기록(ADR) |
| [docs/api/openapi.yaml](docs/api/openapi.yaml) | API 명세 |
| [docs/e2e/](docs/e2e/results.json) | 브라우저 E2E 결과와 스크린샷 |
