# 리드마그넷 CRM 운영 시스템

## 실행 방법

필요한 것: Docker (Compose v2)

```bash
# .env.example을 복사하면서 JWT_SECRET을 새로 만든다(자리표시 값으로는 기동하지 않는다)
sed "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env.example > .env
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
