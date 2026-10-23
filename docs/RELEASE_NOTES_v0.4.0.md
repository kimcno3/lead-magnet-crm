# v0.4.0

v0.3.0 최종 리뷰에서 남은 운영 문제를 닫는 operations 릴리스다. 로그인 시도 제한을 DB로 옮겨 재시작과 여러 인스턴스에서도 유지하고, `/refresh`에도 제한을 걸었으며, 개인정보 암호화·HMAC 키를 서비스 중단을 최소로 하며 바꾸는 절차를 넣었다. **암호문 형식이 바뀌어 되돌릴 수 없다. v0.3.0에서 올릴 때 아래 절을 먼저 읽는다.**

## 기능

- **로그인 시도 제한의 공유 저장소와 `/refresh` 제한** (#66 → PR #70, [ADR 0021](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0021-login-throttle-and-trusted-proxy.md) 변경 기록): 로그인 실패 기록을 PostgreSQL `login_failures`(V13)에 둔다. 서버를 재시작해도 남고 여러 인스턴스가 같은 횟수를 센다. 키는 `LEAD_HMAC_KEY`로 만든 HMAC이고 이메일·IP 원문은 저장하지 않는다. 한도(쌍 5회·이메일 50회·IP 20회, 15분)와 429 `too_many_login_attempts` 규칙은 그대로다. 5분마다 창이 지난 행을 지운다. `/refresh`는 클라이언트 IP당 분당 30회를 넘으면 429 `too_many_requests`를 주고, 관리자 화면은 이 429를 로그아웃이 아니라 "다시 시도"로 보여 준다.
- **개인정보 키 교체** (#67 → PR #69, [ADR 0022](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0022-pii-key-rotation.md)): 새로 쓰는 암호문은 키 id를 담은 형식 2다. `LEAD_ENC_KEY_ID`, `LEAD_ENC_PREVIOUS_KEYS`로 암호화 키를 서비스를 멈추지 않고 바꾸고, `POST /api/admin/pii/reencrypt[?dryRun=true]`가 예전 키의 신청을 새 키로 다시 암호화한다(남은 건수 응답, 감사 `pii.reencrypt`). `LEAD_HMAC_PREVIOUS_KEY`를 둔 채 기동하면 요청을 받기 전에 중복 판정 해시를 새 키로 다시 계산한다. 절차는 [GUIDE 2.11](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/GUIDE.md#211-개인정보-키-교체), 문제 해결은 TROUBLESHOOTING 11절.
- **감사 액션 목록의 OpenAPI 생성과 새 감사 행의 IP 검증** (#65 → PR #68): 감사 로그 API의 `action` 파라미터 enum을 `AuditAction`에서 만든다(손으로 적은 목록 제거). `form.publish`, `template.version.upload`, `creator.archive`, `auth.logout` 감사 행의 IP가 신뢰 프록시 아래에서도 맞는지 테스트가 확인한다.
- **릴리스 준비** (#71 → PR #72): 이 릴리스 노트, OVERVIEW 12절, OpenAPI `info.version` 0.4.0, GUIDE 2.11의 compose 명령, E2E 재실행.

API 추가는 `POST /api/admin/pii/reencrypt`, 오류 코드 `too_many_requests`(429, `/refresh`), 감사 액션 `pii.reencrypt`다.

## v0.3.0에서 올릴 때 (반드시 읽기)

순서대로 확인한다.

1. **먼저 DB를 백업한다.** v0.4.0을 한 번 띄운 뒤에는 되돌릴 수 없다.
2. **되돌리기(다운그레이드)와 v0.3.0·v0.4.0 혼합 운영은 하지 않는다.** v0.4.0은 키를 바꾸지 않아도(기본 설정, 키 id 1) 첫 기동부터 새 신청을 형식 2 암호문으로 저장한다. v0.3.0은 형식 2를 읽지 못해 명단이 "복호화 불가"가 되고 원문 보기가 500을 낸다. 인스턴스를 하나씩 바꾸는 롤링 배포도 하지 않는다. 모두 내리고 새 버전으로 올린다.
3. **`.env`는 바꾸지 않아도 된다.** 새 환경 변수 세 개(`LEAD_ENC_KEY_ID`, `LEAD_ENC_PREVIOUS_KEYS`, `LEAD_HMAC_PREVIOUS_KEY`)는 모두 선택이고, 비우면 "키 교체 없음"이다. 기존 v0.3.0 `.env`로 그대로 뜬다. 필수 키 4개(`JWT_SECRET`, `LEAD_ENC_KEY`, `LEAD_HMAC_KEY`, `FORM_TOKEN_KEY`)도 같다.
4. **V13이 자동으로 적용된다.** `login_failures` 테이블과 인덱스 두 개만 더하는 변경이라 데이터 작업은 없다.
5. **v0.3.0의 인메모리 로그인 실패 횟수는 넘어오지 않는다.** 업그레이드 직후에는 모두 0에서 시작한다.
6. **`app.auth.login-throttle.max-keys` 설정은 없어졌다.** 값을 덮어쓰고 있었다면 무시된다(오류도 나지 않는다). 지워도 된다.
7. **docker compose에서 키를 바꿀 때는 `restart`가 아니라 `up -d`를 쓴다.** `.env`를 고친 뒤:
   - 암호화 키 교체: `docker compose up -d backend`. 컨테이너가 새 환경 변수로 다시 만들어진다.
   - HMAC 키 교체: `docker compose stop backend`로 먼저 내린 뒤 `docker compose up -d backend`.
   - **`docker compose restart backend`는 쓰지 않는다.** `restart`는 컨테이너의 예전 환경을 그대로 두므로 새 `.env` 값이 적용되지 않는다. 기동은 통과하고 계속 이전 키로 저장하며, HMAC 교체는 일어나지 않는다. 오류 없이 지나가 알아채기 어렵다.

## 마이그레이션

V13만 추가됐다. `login_failures(id, key_hash, failed_at)`와 인덱스 두 개를 만드는 additive 변경이다. backend가 기동하면 Flyway가 적용한다. 암호문 형식은 스키마가 아니라 값의 형식이므로 마이그레이션이 없다. 형식 1(v0.3.0)로 저장된 신청은 키 id 1로 읽힌다.

## 테스트

- backend `./gradlew build -Dopenapi.verify=true`: 99개 테스트 클래스, 825개 테스트(실패 0, verify 모드에서 export 쓰기 테스트 1건은 건너뜀)
- admin-web `npm test`: 18개 파일, 166개 테스트 통과
- 브라우저 E2E(Playwright): 24단계 모두 통과, 새 볼륨에서 로컬 실행 ([docs/e2e](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/e2e/results.json))

## 알려진 한계

- **HMAC 키 교체는 서비스가 멈춘다.** 모든 인스턴스를 내린 뒤 하나를 새 설정으로 띄워 해시를 다시 계산한다. 이때 로그인 시도 제한의 15분 실패 횟수가 한 번 초기화된다([ADR 0022](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0022-pii-key-rotation.md)).
- **기동 중 straggler 경합.** HMAC 교체 뒤에도 `LEAD_HMAC_PREVIOUS_KEY`를 둔 채 다른 인스턴스가 이전 키로 신청을 받는 운영 실수가 있으면, 기동 시 그 행을 다시 계산하다가 방금 들어온 신청과 부딪혀 유니크 위반으로 그 인스턴스의 기동이 실패할 수 있다. 오류 로그에는 평문이 아니라 해시가 실린다. GUIDE 2.11의 절차(모든 인스턴스 중지)를 지키면 생기지 않는다. v0.5.0 후보로 `DuplicateKeyException`을 잡아 안내 오류로 바꾸는 일을 남겼다.
- **재암호화는 요청 안에서 동기로 돈다.** 신청이 많으면 admin-web의 nginx 기본 `proxy_read_timeout`(60초)을 넘겨 응답이 끊길 수 있다. backend는 계속 처리하고 중단돼도 이어서 하므로 `?dryRun=true`로 남은 건수를 보며 `remaining`이 0이 될 때까지 다시 부른다.
- 제출 rate limit과 `/refresh` 제한은 여전히 인메모리·인스턴스별이다. 서버를 여러 대 두면 한도가 대수만큼 커진다.
- 배포(Render + Neon 또는 GHCR, dev·prod 프로필)와 실제 `TRUSTED_PROXIES`는 아직 없다.
- 추가 연락처 항목은 가릴 뿐 암호화하지 않는다.

다음 후보는 [WORKPLAN](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/WORKPLAN.md)의 "다음 작업 (v0.5.0 후보)"에 있다. 자세한 설계는 [OVERVIEW](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/OVERVIEW.md) 12절과 [ADR](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/README.md).
