# 리드마그넷 CRM 운영 시스템

운영자가 AI로 만든 신청 폼 HTML을 올려 캠페인과 인스타그램·X·유튜브·스레드 배포 링크를 만들고, 들어온 신청을 암호화된 CRM 명단으로 모아 채널·크리에이터별 성과를 보는 시스템입니다. 글로우업리즈 개발자 채용 과제 제출물입니다.

문서: [GitHub 위키](https://github.com/kimcno3/lead-magnet-crm/wiki) · [사용 가이드](https://claude.ai/artifact/TtUgcd4xd4J9pKPnADsXfk) · [결정 기록(ADR)](docs/adr/README.md) · [문서 목록](docs/README.md)

## 실행 방법

### 준비물

| 필요한 것 | 확인 명령 |
|---|---|
| Git | `git --version` |
| Docker Desktop(또는 Docker Engine + Compose v2), 실행 중일 것 | `docker compose version` |
| OpenSSL(macOS·Linux 기본 포함, Windows는 WSL 또는 Git Bash) | `openssl version` |
| 비어 있는 포트 5173, 8080, 8081, 5432 | |

### 처음 실행하기

**1. 저장소를 받습니다.**

```bash
git clone https://github.com/kimcno3/lead-magnet-crm.git
cd lead-magnet-crm
```

**2. 실행 스크립트를 돌립니다.**

```bash
scripts/local-up.sh
```

스크립트가 하는 일은 다음과 같습니다.

1. `.env`가 없으면 `.env.example`을 복사하면서 비밀 키 4개(JWT 서명, 개인정보 암호화, 중복 판정, 폼 토큰)를 새로 만들고, **예시 데이터(`DEMO_DATA=true`)를 켭니다.**
2. `docker compose up --build -d`로 PostgreSQL → backend → 관리자 화면 순서로 띄웁니다. **처음에는 이미지 빌드로 몇 분**, 그 뒤로는 1분 안쪽입니다.
3. backend와 관리자 화면이 응답할 때까지 기다린 뒤 아래처럼 접속 주소를 알려 줍니다.

```
준비됐습니다.

  관리자 화면   http://localhost:5173
                로그인: admin@example.com / 비밀번호는 .env의 ADMIN_SEED_PASSWORD
  API 문서      http://localhost:8080/swagger-ui.html
  공개 폼       http://localhost:8081/f/{slug}
  예시 데이터가 들어 있습니다(DEMO_DATA=true, 빈 DB에 한 번만). …
```

**3. 관리자 화면에 로그인합니다.**

http://localhost:5173 을 열고 `admin@example.com`으로 로그인합니다. 비밀번호는 아래 명령으로 확인합니다(기본값 `change-me-local-only`).

```bash
grep ADMIN_SEED_PASSWORD .env
```

**4. 예시 데이터로 둘러봅니다.**

글로우업리즈 사업에 맞춘 캠페인 4개가 들어 있습니다(크리에이터와 신청자는 모두 가상 인물입니다).

| 캠페인 | 볼 수 있는 것 |
|---|---|
| 아카데미 · 유튜브 첫 1,000명 로드맵 | 채널별·크리에이터별 성과, 신청 47건, 크리에이터 링크 |
| MCN · 크리에이터 파트너 모집 | 크리에이터 링크, 보관된 크리에이터의 지난 성과 |
| 요고 · 브랜드 입점·공동구매 | 게시된 폼과 초안 폼, 보존 기간 180일 |
| F&B · 성수 매장 오픈 시식회 | 마감된 폼, 보관된 캠페인 |

캠페인을 열어 **성과 → 폼 → 명단** 탭을 차례로 보고, 왼쪽 메뉴의 **크리에이터**, **템플릿**, **감사 로그**도 확인해 보세요.

**5. 방문자로 신청해 봅니다.**

http://localhost:8081/f/academy-first-1000 을 새 창(가능하면 시크릿 창)에서 열고 신청합니다. 폼을 연 뒤 2초 이상 지나 제출해야 합니다(봇 방지). 관리자 화면 **명단** 탭에 새 신청이 생기고 **성과** 숫자가 오릅니다.

### 다시 실행, 종료, 초기화

| 하고 싶은 것 | 명령 |
|---|---|
| 멈추기(데이터 유지) | `docker compose down` |
| 다시 띄우기 | `scripts/local-up.sh` |
| DB를 비우고 처음부터(예시 데이터 다시 생성) | `scripts/local-up.sh --fresh` |
| 예시 데이터 없이 빈 DB로 | `.env`에서 `DEMO_DATA=false`로 바꾼 뒤 `scripts/local-up.sh --fresh` |

`.env`는 다시 만들지 마세요. 키가 바뀌면 이전 DB의 신청자 정보를 읽지 못해 backend가 멈춥니다. 예전에 이 저장소를 띄운 적이 있는 PC에서 새로 클론했다면 스크립트가 이를 알려 주고 멈춥니다. 이때는 `scripts/local-up.sh --fresh`로 시작합니다.

### 스크립트 없이 직접 실행하기

```bash
sed -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" \
    -e "s|^LEAD_ENC_KEY=.*|LEAD_ENC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^LEAD_HMAC_KEY=.*|LEAD_HMAC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^FORM_TOKEN_KEY=.*|FORM_TOKEN_KEY=$(openssl rand -base64 32)|" \
    -e "s|^DEMO_DATA=.*|DEMO_DATA=true|" .env.example > .env
docker compose up --build
```

### 잘 안 될 때

| 증상 | 해결 |
|---|---|
| `port is already allocated` | 해당 포트를 쓰는 프로그램을 끄거나, `.env`의 `ADMIN_WEB_PORT`·`ADMIN_PORT`·`PUBLIC_PORT`·`POSTGRES_PORT`를 바꿉니다 |
| 스크립트가 "이전에 띄운 DB가 남아 있는데 .env가 없습니다"로 멈춤 | `scripts/local-up.sh --fresh` |
| backend 로그에 `기존 데이터를 암호화한 키와 다릅니다` | `.env`를 다시 만든 경우입니다. 이전 `.env`로 되돌리거나 `--fresh` |
| `Cannot connect to the Docker daemon` | Docker Desktop을 켭니다 |

더 많은 경우는 [문제 해결](docs/TROUBLESHOOTING.md)에 있습니다.

## 테스트 방법

필요한 것: JDK 17 이상(빌드 때 JDK 21을 자동으로 받습니다), Node.js 24, 실행 중인 Docker(backend 테스트가 Testcontainers로 PostgreSQL을 띄웁니다).

```bash
# backend (실제 PostgreSQL로 통합 테스트)
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

브라우저 E2E(Playwright, Chromium)는 시스템을 띄운 뒤 실행합니다. 결과와 스크린샷은 `docs/e2e/`에 남습니다.

```bash
scripts/local-up.sh
cd e2e
npm ci
npx playwright install chromium
npm test
```
