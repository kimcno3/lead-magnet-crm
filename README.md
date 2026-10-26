<div align="center">

# 📮 리드마그넷 CRM 운영 시스템

AI로 만든 신청 폼 HTML을 올려 캠페인과 SNS 배포 링크를 만들고,<br>
들어온 신청을 암호화된 CRM 명단으로 모아 **채널·크리에이터별 성과**를 보는 시스템입니다.

[![CI](https://github.com/kimcno3/lead-magnet-crm/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kimcno3/lead-magnet-crm/actions/workflows/ci.yml)
[![E2E](https://github.com/kimcno3/lead-magnet-crm/actions/workflows/e2e.yml/badge.svg?branch=main)](https://github.com/kimcno3/lead-magnet-crm/actions/workflows/e2e.yml)
[![Release](https://img.shields.io/github/v/release/kimcno3/lead-magnet-crm)](https://github.com/kimcno3/lead-magnet-crm/releases/latest)

📖 [GitHub 위키](https://github.com/kimcno3/lead-magnet-crm/wiki) &nbsp;·&nbsp;
🧭 [사용 가이드](https://claude.ai/artifact/TtUgcd4xd4J9pKPnADsXfk) &nbsp;·&nbsp;
🧱 [결정 기록(ADR)](docs/adr/README.md) &nbsp;·&nbsp;
🗂️ [문서 목록](docs/README.md)

</div>

---

## 🚀 실행 방법

### ✅ 준비물

| 필요한 것                                                         | 확인 명령                |
| :---------------------------------------------------------------- | :----------------------- |
| Git                                                               | `git --version`          |
| Docker Desktop (또는 Docker Engine + Compose v2), **실행 중일 것** | `docker compose version` |
| OpenSSL (macOS·Linux 기본 포함, Windows는 WSL 또는 Git Bash)       | `openssl version`        |
| 비어 있는 포트 `5173` `8080` `8081` `5432`                         | —                        |

### ▶️ 처음 실행하기

#### 1️⃣ 저장소 받기

```bash
git clone https://github.com/kimcno3/lead-magnet-crm.git
cd lead-magnet-crm
```

#### 2️⃣ 실행 스크립트 돌리기

```bash
scripts/local-up.sh
```

스크립트가 하는 일:

1. 🔑 `.env`가 없으면 비밀 키 4개(JWT 서명·개인정보 암호화·중복 판정·폼 토큰)를 새로 만들고 **예시 데이터를 켭니다**
2. 🐳 `docker compose up --build -d`로 PostgreSQL → backend → 관리자 화면 순서로 띄웁니다
3. ⏳ 모두 응답할 때까지 기다린 뒤 접속 주소를 알려 줍니다

> [!NOTE]
> 처음에는 이미지 빌드로 **3분 안팎** 걸리고, 그 뒤로는 1분 안쪽입니다.

준비되면 이렇게 나옵니다:

```text
준비됐습니다.

  관리자 화면   http://localhost:5173
                로그인: admin@example.com / 비밀번호는 .env의 ADMIN_SEED_PASSWORD
  API 문서      http://localhost:8080/swagger-ui.html
  공개 폼       http://localhost:8081/f/{slug}
  예시 데이터가 들어 있습니다(DEMO_DATA=true, 빈 DB에 한 번만). …
```

#### 3️⃣ 관리자 화면 로그인

| 항목     | 값                                                    |
| :------- | :---------------------------------------------------- |
| 주소     | http://localhost:5173                                 |
| 이메일   | `admin@example.com`                                   |
| 비밀번호 | `change-me-local-only` (확인: `grep ADMIN_SEED_PASSWORD .env`) |

로그인하면 **📊 유입 현황 대시보드**가 첫 화면으로 열립니다.

#### 4️⃣ 예시 데이터 둘러보기

글로우업리즈 사업에 맞춘 캠페인 4개가 들어 있습니다. 크리에이터와 신청자는 모두 **가상 인물**입니다.

| 캠페인                              | 볼 수 있는 것                                         |
| :---------------------------------- | :---------------------------------------------------- |
| 🎓 아카데미 · 유튜브 첫 1,000명 로드맵 | 채널별·크리에이터별 성과, 신청 47건, 크리에이터 링크    |
| 🤝 MCN · 크리에이터 파트너 모집       | 크리에이터 링크, 보관된 크리에이터의 지난 성과          |
| 🛍️ 요고 · 브랜드 입점·공동구매        | 게시된 폼과 초안 폼, 보존 기간 180일                   |
| 🍽️ F&B · 성수 매장 오픈 시식회        | 마감된 폼, 보관된 캠페인                               |

캠페인을 열어 **성과 → 폼 → 명단** 탭을 차례로 보고, 왼쪽 메뉴의 **크리에이터 · 템플릿 · 감사 로그**도 확인해 보세요.

#### 5️⃣ 방문자로 신청해 보기

1. 시크릿 창에서 http://localhost:8081/f/academy-first-1000 을 엽니다.
2. 폼을 작성하고 동의한 뒤 신청합니다.
3. 관리자 화면의 **명단** 탭에 새 신청이 생기고 **성과** 숫자가 오릅니다.

> [!TIP]
> 봇 방지를 위해 폼을 연 뒤 **2초 이상** 지나서 제출해야 합니다.

### 🔁 다시 실행 · 종료 · 초기화

| 하고 싶은 것                         | 명령                                                         |
| :----------------------------------- | :----------------------------------------------------------- |
| ⏸️ 멈추기 (데이터 유지)               | `docker compose down`                                        |
| ▶️ 다시 띄우기                        | `scripts/local-up.sh`                                        |
| 🧹 DB를 비우고 처음부터 (예시 데이터 다시 생성) | `scripts/local-up.sh --fresh`                       |
| 🫙 예시 데이터 없이 빈 DB로            | `.env`에서 `DEMO_DATA=false`로 바꾼 뒤 `scripts/local-up.sh --fresh` |

> [!WARNING]
> `.env`는 다시 만들지 마세요. 키가 바뀌면 이전 DB의 신청자 정보를 읽지 못해 backend가 멈춥니다.
> 예전에 이 저장소를 띄운 적이 있는 PC에서 새로 클론했다면 스크립트가 이를 알려 주고 멈춥니다. 이때는 `scripts/local-up.sh --fresh`로 시작하세요.

<details>
<summary>🛠️ 스크립트 없이 직접 실행하기</summary>

```bash
sed -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" \
    -e "s|^LEAD_ENC_KEY=.*|LEAD_ENC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^LEAD_HMAC_KEY=.*|LEAD_HMAC_KEY=$(openssl rand -base64 32)|" \
    -e "s|^FORM_TOKEN_KEY=.*|FORM_TOKEN_KEY=$(openssl rand -base64 32)|" \
    -e "s|^DEMO_DATA=.*|DEMO_DATA=true|" \
    .env.example > .env

docker compose up --build
```

</details>

### 🆘 잘 안 될 때

| 증상                                                           | 해결                                                                                       |
| :------------------------------------------------------------- | :----------------------------------------------------------------------------------------- |
| `port is already allocated`                                    | 해당 포트를 쓰는 프로그램을 끄거나 `.env`의 `ADMIN_WEB_PORT` `ADMIN_PORT` `PUBLIC_PORT` `POSTGRES_PORT`를 바꿉니다 |
| 스크립트가 "이전에 띄운 DB가 남아 있는데 .env가 없습니다"로 멈춤 | `scripts/local-up.sh --fresh`                                                              |
| backend 로그에 `기존 데이터를 암호화한 키와 다릅니다`            | `.env`를 다시 만든 경우입니다. 이전 `.env`로 되돌리거나 `--fresh`                            |
| `Cannot connect to the Docker daemon`                          | Docker Desktop을 켭니다                                                                     |

더 많은 경우는 👉 [문제 해결](docs/TROUBLESHOOTING.md)

---

## 🧪 테스트 방법

| 필요한 것 | 비고                                                    |
| :-------- | :------------------------------------------------------ |
| JDK 17+   | 빌드 때 JDK 21을 자동으로 받습니다                       |
| Node.js 24 | 관리자 화면·E2E                                         |
| Docker    | 실행 중일 것 (backend 테스트가 Testcontainers로 PostgreSQL을 띄웁니다) |

**☕ backend** — 실제 PostgreSQL로 통합 테스트

```bash
cd backend
./gradlew test
```

**🖥️ admin-web** — lint · 타입 검사 · 테스트 · 빌드

```bash
cd admin-web
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

**🌐 브라우저 E2E** — Playwright(Chromium), 시스템을 띄운 뒤 실행. 결과와 스크린샷은 `docs/e2e/`에 남습니다.

```bash
scripts/local-up.sh
cd e2e
npm ci
npx playwright install chromium
npm test
```
