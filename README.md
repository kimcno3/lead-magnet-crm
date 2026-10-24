# 리드마그넷 CRM 운영 시스템

## 실행 방법

필요한 것: Docker (Compose v2)

가장 빠른 방법은 스크립트 하나입니다. `.env`가 없으면 키를 새로 만들고, 전체 시스템을 띄운 뒤 준비되면 접속 주소를 알려 줍니다(`--fresh`는 DB를 비우고 새로 띄움).

```bash
scripts/local-up.sh
```

수동으로 하려면:

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

브라우저 E2E(Playwright, Chromium)는 시스템을 띄운 뒤 실행한다. 결과는 `docs/e2e/`에 남는다.

```bash
scripts/local-up.sh
cd e2e
npm ci
npx playwright install chromium
npm test
```
