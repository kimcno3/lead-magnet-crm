# v0.3.0

v0.2.0의 최종 리뷰에서 남은 운영 안전 문제를 닫는 hardening 릴리스다. 로그인 비밀번호 대입을 제한하고, 프록시 뒤에서도 실제 클라이언트 IP를 기록하며, `payload`에 남는 연락처 모양 값을 가리고, 운영 행위 감사의 공백을 메웠다. 브라우저 E2E는 CI에서 돈다. **compose 구성이 바뀌므로 v0.2.0에서 올릴 때 아래 절을 먼저 읽는다.**

## 기능

- **로그인 시도 제한과 신뢰 프록시 뒤 실제 IP** (#51 → PR #57, [ADR 0021](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0021-login-throttle-and-trusted-proxy.md)): 로그인 실패를 15분 슬라이딩 윈도로 센다. (이메일, IP) 쌍 5회, 이메일 하나에 모든 IP 합쳐 50회, IP 하나 20회를 넘으면 429 `too_many_login_attempts`와 `Retry-After`를 돌려주고 관리자 화면이 "잠시 후 다시 시도해 주세요(약 N분)"를 보여 준다. 잠금이 시작될 때만 감사 로그에 `auth.login.locked` 한 줄을 남긴다. `ClientIpFilter`는 `TRUSTED_PROXIES`에서 온 admin 요청의 `X-Forwarded-For`만 믿고, public 포트는 `X-Forwarded-For`를 읽지 않는다. 감사 로그 IP와 제한의 IP가 같은 값이다.
- **payload의 연락처 모양 추가 항목** (#52 → PR #58, [ADR 0015](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0015-pii-encryption.md) 변경 기록): `phone`·`email`이 아닌 이름으로 전화·이메일처럼 보이는 입력이 있으면 업로드 검사가 `extra_contact_field` 경고를 낸다. 명단 응답과 마스킹 CSV는 해당 `payload` 값을 같은 규칙으로 가리고, 원문 조회 API(`extraContacts`)와 원문 CSV는 원문을 준다. 이 값은 암호화하지 않는다.
- **감사 공백 메우기** (#53 → PR #60, [ADR 0016](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0016-audit-log.md) 변경 기록): `auth.logout`, `auth.refresh.reused`, `form.publish`, `form.close`, `template.version.upload`, `creator.archive`를 기록한다. 로그아웃과 refresh 재사용은 살아 있는 토큰을 실제로 폐기했을 때만 남는다.
- **Playwright E2E를 CI에 통합** (#54 → PR #59): `.github/workflows/e2e.yml`이 docker compose로 스택을 올려 브라우저 E2E를 실행하고 결과를 아티팩트 `e2e-results`로 남긴다.
- **크리에이터 `utm_content`와 패키지 의존 방향 정리** (#55 → PR #61, [ADR 0014](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0014-creator-attribution.md) 변경 기록): 크리에이터 링크가 `utm_content = creator-{creator_id}`를 갖는다(`LinkResponse.utmContent`). `DateRange`, `AuditActor`, 템플릿 새 버전 컨트롤러의 위치를 정리했고 `PackageDependencyRulesTest`가 패키지 의존 규칙을 지킨다.
- **릴리스 준비** (#62 → PR #63): 이 릴리스 노트, OVERVIEW 11절, OpenAPI `info.version` 0.3.0, ADR 0021의 한계 기록, 문제 해결 항목, E2E 재실행.

API 추가는 `LinkResponse.utmContent`, `LeadContactResponse.extraContacts`, 오류 코드 `too_many_login_attempts`(429), 새 감사 액션이다.

## v0.2.0에서 올릴 때 (반드시 읽기)

DB는 특별한 절차가 없다(아래 마이그레이션). 바뀌는 것은 docker compose 구성이다. 순서대로 한다.

1. **기존 네트워크를 지우고 다시 만든다.** compose 기본 네트워크가 고정 대역(`172.16.57.0/24`)이 되고 admin-web이 고정 주소 `172.16.57.10`을 갖는다. v0.2.0에서 만든 `lead-magnet-crm_default` 네트워크가 남아 있으면 충돌하므로 먼저 `docker compose down`을 실행한다. **`-v`는 붙이지 않는다**(붙이면 DB 볼륨이 지워진다). 그다음 `docker compose up --build` 한다. 이 대역이 VPN이나 다른 네트워크와 겹치면 대역, admin-web의 `ipv4_address`, `TRUSTED_PROXIES`를 함께 바꾼다.
2. **:8080과 :5432는 이제 호스트 루프백(127.0.0.1)에서만 열린다.** 다른 컴퓨터에서 admin API나 Postgres에 직접 닿던 구성은 더 이상 되지 않는다. 같은 네트워크의 다른 컴퓨터는 :5173(admin-web)으로 들어온다.
3. **`TRUSTED_PROXIES`.** 기본값은 `172.16.57.10/32`(admin-web 컨테이너 하나)이고 `.env.example`에 있다. compose가 아닌 다른 프록시나 로드 밸런서 뒤에서 돌리면 그 hop의 주소로 바꾼다. 대역 전체로 넓히지 않는다(게이트웨이 `.1`이 들어간다). **값이 실제 앞단 프록시와 다르면 backend는 모든 요청을 프록시 한 주소에서 온 것으로 보므로 모든 로그인이 한 IP를 공유한다. 그러면 IP 한도(15분 20회 실패)가 전체 사용자의 한도가 되어 실패가 쌓이면 모든 사용자가 잠길 수 있다.** 감사 로그의 IP도 모두 프록시 주소로 남는다.
4. **`--wait`를 지원하지 않는 오래된 docker compose.** CI는 `docker compose up --build -d --wait --wait-timeout 300`을 쓴다. 로컬에서는 `docker compose up --build -d`로 올리고 backend가 `healthy`가 될 때까지(`docker compose ps`) 기다린다. 이 플래그가 없다는 오류는 [문제 해결](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/TROUBLESHOOTING.md)에 있다.

필수 키 4개(`JWT_SECRET`, `LEAD_ENC_KEY`, `LEAD_HMAC_KEY`, `FORM_TOKEN_KEY`)는 v0.2.0과 같다. 기존 `.env`와 키를 그대로 쓴다.

## 마이그레이션

V12만 추가됐다. 크리에이터가 있고 `utm_content`가 비어 있는 링크를 `creator-{creator_id}`로 채우는 **데이터 변경(`UPDATE`)이며 스키마는 바뀌지 않는다.** 이미 값이 있는 행은 건드리지 않으므로 여러 번 실행해도 같다. DB 업그레이드 절차는 없고 backend가 기동하면 Flyway가 적용한다.

## 테스트

- backend `./gradlew build -Dopenapi.verify=true`: 91개 테스트 클래스, 768개 테스트(실패 0, verify 모드에서 export 쓰기 테스트 1건은 건너뜀)
- admin-web `npm test`: 18개 파일, 162개 테스트 통과
- 브라우저 E2E(Playwright): 24단계 모두 통과, 새 볼륨에서 로컬 실행 ([docs/e2e](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/e2e/results.json))

## 알려진 한계

- 로그인 시도 제한은 인메모리이고 인스턴스별이다. 재시작하면 기록이 지워지고, 서버를 여러 대 두면 한도가 대수만큼 커진다. 키는 10,000개까지만 들고(LRU) 넘으면 가장 오래 안 쓴 것부터 잊으므로, 이메일·IP를 대량으로 바꾸는 공격은 쌍·IP 카운터를 밀어낼 수 있다.
- `/refresh`는 제한하지 않는다. `/login`만 제한한다.
- 공개 포트는 `X-Forwarded-For`를 읽지 않는다. 공개 포트 앞에 프록시를 두려면 [ADR 0021](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0021-login-throttle-and-trusted-proxy.md)을 다시 본다.
- 추가 연락처 항목은 가릴 뿐 암호화하지 않는다.
- PII 키 교체 절차와 배포는 아직 없다.

다음 후보는 [WORKPLAN](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/WORKPLAN.md)의 "다음 작업 (v0.4.0 후보)"에 있다. 자세한 설계는 [OVERVIEW](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/OVERVIEW.md) 11절과 [ADR](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/README.md).
