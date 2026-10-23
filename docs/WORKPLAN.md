# 작업 계획과 진행 현황

전체 계획과 결정 이유는 [PLAN.md](PLAN.md), 이번 범위 밖은 [ROADMAP.md](ROADMAP.md)에 있다. 이 문서는 무엇이 끝났고 다음에 무엇을 할지를 한눈에 보여준다. 이슈와 PR은 [GitHub](https://github.com/kimcno3/lead-magnet-crm)에 있다.

## 진행 방식

1. 이슈마다 작업 지시서를 쓴다([plans/](plans/)).
2. 구현은 테스트를 먼저 쓰고(TDD), 기능 브랜치에서 커밋한다.
3. 별도 리뷰어가 요구사항 일치와 코드 품질을 검토하고, 지적이 있으면 수정 후 다시 검토한다.
4. PR 템플릿 체크리스트를 채워 develop으로 squash 병합한다. CI(backend build·test·OpenAPI 검증, admin-web lint·typecheck·test·build)를 통과해야 한다.
5. 마일스톤이 끝나면 전체 리뷰를 한 번 더 하고 develop → main 릴리스 PR과 태그를 단다.

## v0.1.0 core (완료)

| 이슈 | 내용 | PR |
|---|---|---|
| #1 | 저장소 골격과 로컬 Docker 실행 환경 | #20 |
| #2 | Flyway V1 스키마, 프로필 분리, 운영자 시드 | #21 |
| #3 | 인증: JWT, refresh 교체·재사용 감지 | #22 |
| #4 | HTML 템플릿 업로드와 폼 계약 검증 | #23 |
| #5 | 캠페인과 폼 생성·게시·마감 | #24 |
| #6 | 채널별 배포 링크와 /l/{code} | #25 |
| #7 | 공개 출처 격리, 샌드박스 래퍼, 방문 추적 | #26 |
| #8 | 신청 접수 검증, 채널 귀속, 중복 방지, 명단 | #27 |
| #9 | 캠페인·채널별 성과 지표 | #28 |
| #10 | 성공·실패·격리 통합 시나리오 | #29 |
| #30 | 백엔드 전체 최종 리뷰 반영(NUL 입력, value 없는 체크박스, CSP 강화 등) | #31 |
| #11 | 관리자 화면 | #32 |
| #12 | ADR 정리, OVERVIEW, OpenAPI, README | #33 |
| #35 | 흑백 색상 체계 | #36 |
| #34 | Playwright 브라우저 E2E와 스크린샷 | #37 |
| #38 | 사용 가이드, 유스케이스, 문제 해결, 작업 계획 문서 | 이 문서 |

결과 지표: backend 테스트 360건, admin-web 78건, 브라우저 E2E 22단계(스크린샷 [e2e/screenshots](e2e/screenshots/)).

## v0.2.0 extras (완료)

회사의 사업 구조(크리에이터 R/S 성과형 협업, 개인정보를 다루는 CRM)에 직접 연결되는 것만 골랐다.

| 순위 | 이슈 | 작업 | 왜 필요한가 | 상태 |
|---|---|---|---|---|
| 1 | #13 | 크리에이터별 성과 추적: creators 테이블, 크리에이터 × 채널 링크, 크리에이터별 지표 | R/S 정산의 근거. "어느 크리에이터가 몇 명을 데려왔나"를 증명 | 완료 (#41) |
| 2 | #14 | 개인정보 암호화(AES-GCM) + 중복 판정용 HMAC, 조회 시 마스킹, 원문 열람 API 분리 | DB 유출 시 피해 최소화, 기본 화면에서 원문 노출 줄이기 | 완료 (14a expand #42, 14b contract #43) |
| 3 | #15 | 명단 열람·다운로드 감사 로그 | 누가 언제 개인정보를 봤는지 추적(개인정보보호법 대응). 명단 CSV 다운로드(마스킹/원문) 포함 | 완료 (PR #44) |
| 4 | #16 | 업로드 HTML 검사 리포트(폼 계약, 외부 전송·외부 리소스 경고)와 운영자용 AI 프롬프트 가이드 | 업로드 실패를 줄이고, CSP로 막히는 외부 리소스를 미리 알려줌 | 완료 (PR #45) |
| 5 | #17 | 게시 시 템플릿 스냅샷 | 게시 후 템플릿이 바뀌어도 공개 폼이 흔들리지 않게 | 완료 (PR #46) |
| 6 | #18 | 데이터 품질 방어: honeypot, rate limit, 봇 방문 제외 | 지표 왜곡과 스팸 신청 방지 | 완료 (PR #47) |
| 7 | #19 | 보존기한 파기(익명화)와 동의 문구 버전 기록 | 수집 목적 달성 후 파기 의무, 동의 증빙 | 완료 (PR #48) |

v0.2.0 extras 7건을 모두 마쳤다. 마지막에 전체 브랜치 리뷰에서 나온 값싼 보강을 함께 넣었다(#49): JWT_SECRET이 다른 키와 같으면 기동 실패, 원문 CSV 전화번호 하이픈 형식, `visits.link_id`·`leads.link_id` 인덱스(V11), 테스트 소음 정리, 업그레이드 경로 문서(OVERVIEW 10절, TROUBLESHOOTING), 릴리스 노트(`RELEASE_NOTES_v0.2.0.md`). 결과 지표: backend 테스트 677건(79개 클래스), admin-web 155건(18개 파일), 브라우저 E2E 24단계.

## v0.3.0 hardening (완료)

v0.2.0 최종 리뷰에서 나왔지만 그때 하지 않은 것 가운데 위험이 큰 것부터 닫았다.

| 순위 | 작업 | 우선순위 | 왜 필요한가 | 상태 |
|---|---|---|---|---|
| 1 | 로그인 시도 제한(throttling) | 높음 | 비밀번호 대입을 막는 장치가 없었다. 감사 로그가 있어도 시도를 막지는 못한다 | 완료 (#51, PR #57) |
| 2 | 감사 로그의 IP를 nginx 뒤에서도 정확히(신뢰 프록시 `TRUSTED_PROXIES`) | 높음 | `getRemoteAddr()`만 써서 프록시 뒤에서는 프록시 IP만 남았다. 제출 rate limit도 같은 문제 | 완료 (#51, PR #57) |
| 3 | `payload`의 평문 연락처 처리(`extra_contact_field` 경고와 마스킹) | 높음 | 폼에 전화·이메일 모양의 추가 항목이 있으면 암호화 대상 밖인 `payload`에 평문으로 남는다 | 완료 (#52, PR #58) |
| 4 | PII 키 교체 | 중간 | 키를 바꾸면 기동이 막힌다 | v0.4.0 후보로 이동 |
| 5 | 감사 공백 메우기: refresh 재사용 감지, 로그아웃, 템플릿 새 버전, 게시, 크리에이터 보관 | 중간 | 운영 행위 중 기록되지 않는 것이 남아 있었다 | 완료 (#53, PR #60) |
| 6 | 배포(Render + Neon 또는 GHCR), dev·prod 프로필 | 중간 | 로컬 Docker 실행만 된다 | v0.4.0 후보로 이동 |
| 7 | Playwright E2E를 CI에 통합 | 중간 | 브라우저 E2E가 수동 실행이라 회귀를 놓칠 수 있었다 | 완료 (#54, PR #59) |
| 8 | 크리에이터별 `utm_content` | 낮음 | 광고 도구에서 크리에이터를 구분하는 값. 귀속 자체는 `link_id`로 이미 된다 | 완료 (#55, PR #61) |
| 9 | 계층 정리: `DateRange`를 common으로, `template` ↔ `form` 순환, `AuditActor` 위치 | 낮음 | 동작은 맞지만 패키지 의존 방향이 어긋나 있었다 | 완료 (#55, PR #61) |

완료 표시: 1번(로그인 시도 제한)과 2번(nginx 뒤 실제 IP)은 #51에서 함께 끝냈다(PR #57, [ADR 0021](adr/0021-login-throttle-and-trusted-proxy.md)). 3번(payload의 연락처 모양 항목)은 #52에서 끝냈다(PR #58, `extra_contact_field` 경고 + 명단·마스킹 CSV 마스킹, [ADR 0015](adr/0015-pii-encryption.md) 변경 기록). 5번(감사 공백)은 #53에서 끝냈다(PR #60, [ADR 0016](adr/0016-audit-log.md) 변경 기록). 7번(Playwright E2E의 CI 통합)은 #54에서 끝냈다(PR #59, `.github/workflows/e2e.yml`, 결과는 CI 아티팩트 `e2e-results`). 8번(크리에이터 `utm_content`, V12)과 9번(계층 정리)은 #55에서 끝냈다(PR #61): `DateRange`는 `common/DateRange`, `AuditActor`는 `audit/domain`, `AuditActors`는 `audit/api`, 템플릿 새 버전 PUT은 `form/api/TemplateVersionController`로 옮겼고, 키 분리 검사는 `common/crypto/SecretKeyMaterial` 포트로 바꿨으며, 패키지 의존 규칙은 `PackageDependencyRulesTest`가 지킨다. `visits.link_id`·`leads.link_id` 인덱스는 v0.2.0에서 끝냈다(V11).

v0.3.0 hardening 5건(#51 ~ #55)을 모두 마쳤고, 릴리스 준비는 #62(PR #63)에서 했다: 릴리스 노트(`RELEASE_NOTES_v0.3.0.md`), OVERVIEW 11절, OpenAPI `info.version` 0.3.0, ADR 0021의 한계 기록, E2E 재실행. 결과 지표: backend 테스트 768건(91개 클래스, 1건은 verify 모드에서 건너뜀), admin-web 162건(18개 파일), 브라우저 E2E 24단계(새 볼륨, 로컬 실행).

## v0.4.0 operations (완료)

v0.3.0 최종 리뷰에서 나왔지만 그때 하지 않은 것 가운데 운영에 직접 닿는 것을 닫았다. 배포는 외부 계정과 비용 결정이 필요해 v0.5.0 후보로 옮겼다.

| 순위 | 작업 | 우선순위 | 왜 필요한가 | 상태 |
|---|---|---|---|---|
| 1 | PII 키 교체(버전 → 키 맵, 재암호화 배치, HMAC 키 기동 시 재계산) | 중간 | 키를 바꾸면 기동이 막혔다. 유출 시 대응 수단이 없었다 | 완료 (#67, PR #69): 암호문 형식 2(키 id), `POST /api/admin/pii/reencrypt`, 감사 `pii.reencrypt` ([ADR 0022](adr/0022-pii-key-rotation.md), GUIDE 2.11) |
| 2 | 배포(Render + Neon 또는 GHCR), dev·prod 프로필 | 중간 | 지금은 로컬 Docker 실행만 된다 | v0.5.0 후보로 이동 |
| 3 | 로그인 시도 제한의 공유 저장소와 `/refresh` 제한 | 중간 | 인메모리·인스턴스별이라 서버를 여러 대 두면 한도가 대수만큼 커졌고 `/refresh`는 제한이 없었다 | 완료 (#66, PR #70): `login_failures`(V13), `/refresh` IP별 분당 30회(429 `too_many_requests`), [ADR 0021](adr/0021-login-throttle-and-trusted-proxy.md) 변경 기록 |
| 4 | 감사 로그 API의 액션 목록을 `AuditAction` enum에서 OpenAPI로 생성 | 낮음 | 손으로 적은 액션 목록이 enum과 어긋날 수 있었다 | 완료 (#65, PR #68): `AuditOpenApiConfig` |
| 5 | 새 감사 행의 IP 검증 | 낮음 | IP 컬럼을 XFF 아래에서 확인하는 테스트가 `auth.login.failure` 하나뿐이었다 | 완료 (#65, PR #68): `AuditClientIpTest` |

v0.4.0 operations 3건(#65 ~ #67)을 모두 마쳤고, 릴리스 준비는 #71(PR #72)에서 했다: 릴리스 노트(`RELEASE_NOTES_v0.4.0.md`), OVERVIEW 12절, OpenAPI `info.version` 0.4.0, GUIDE 2.11의 compose 명령(`up -d`, `restart` 금지), PLAN 6.4절, E2E 재실행. 결과 지표: backend 테스트 825건(99개 클래스, 1건은 verify 모드에서 건너뜀), admin-web 166건(18개 파일), 브라우저 E2E 24단계(새 볼륨, 로컬 실행).

## 다음 작업 (v0.5.0 후보)

v0.4.0 최종 리뷰에서 나왔지만 이번에 하지 않은 것이다.

| 순위 | 작업 | 우선순위 | 왜 필요한가 |
|---|---|---|---|
| 1 | 배포(Render + Neon 또는 GHCR), dev·prod 프로필 | 중간 | 지금은 로컬 Docker 실행만 된다. 계정과 비용은 저장소 소유자가 정해야 한다. `TRUSTED_PROXIES`는 배포 구성의 실제 프록시 주소에 맞춰 정한다 |
| 2 | 기동 시 straggler 경합 처리 | 낮음 | 운영 실수로 이전 HMAC 키 인스턴스가 신청을 받은 뒤 기동하면, straggler 재계산이 방금 들어온 신청과 부딪혀 유니크 위반으로 기동이 실패할 수 있다(로그에는 해시만 실린다). `LeadHashRotation`에서 `DuplicateKeyException`을 `PiiKeyRotationException`으로 바꿔 안내한다 |
| 3 | 재암호화를 비동기 작업으로 | 중간 | 지금은 요청 안에서 동기로 돈다. 신청이 많으면 admin-web nginx의 `proxy_read_timeout`(기본 60초)을 넘겨 응답이 끊긴다(backend는 계속 처리). 작업 id와 진행률 조회로 바꾸고, 관리자 화면 버튼은 그 뒤에 검토한다 |
| 4 | 인스턴스별 메모리 상태의 공유 저장소 | 낮음 | 제출 rate limit과 `/refresh` 제한은 인메모리·인스턴스별이고, 로그인 제한이 한도에 걸린 키를 기억하는 빠른 거절 캐시도 프로세스 안에 있다(재시작하면 비워지고 DB가 다시 판정한다). 서버를 여러 대 두면 앞의 두 한도가 대수만큼 커진다 |
| 5 | refresh 가족의 절대 수명 | 낮음 | 쓸 때마다 14일이 다시 시작되어 세션이 끝나지 않는다 |
| 6 | HMAC 키를 서비스 중단 없이 교체(해시 컬럼 두 개) | 낮음 | 지금은 모든 인스턴스를 내려야 한다 |
| 7 | KMS·봉투 암호화 | 낮음 | 키가 시크릿 저장소의 환경 변수로 들어온다 |

## 알려진 한계와 후속 개선 (리뷰에서 나온 것)

- 공개 폼은 외부 이미지·웹폰트·외부 CSS를 막는다(보안 우선, ADR 0002). 업로드 단계에서 #16의 검사 리포트가 경고한다([ADR 0017](adr/0017-template-inspection.md)).
- 캠페인 기간(startsAt/endsAt)은 참고용이다. 폼 자동 마감은 하지 않는다(ADR 0013).
- refresh 토큰은 쓸 때마다 14일이 다시 시작되어 세션 절대 수명이 없다(ROADMAP). 로그인 시도 제한은 v0.4.0(#66)부터 DB에 기록해 서버 여러 대가 함께 센다. `/refresh` 제한은 인메모리·인스턴스별이다(ADR 0021).
- 폼당 채널별 링크 1개 규칙(크리에이터 포함 시 폼 × 채널 × 크리에이터 1개)은 애플리케이션에서만 지킨다(동시 클릭 시 중복 가능성, ADR 0006·0014).
- 관리자 화면: 저장하지 않고 떠날 때 경고가 없다. 로그인 화면은 서버 연결 실패를 제출 시점에만 알린다.
- 제출 rate limit은 인메모리·인스턴스별이다. 공개 포트에는 앞단 프록시가 없다고 보고 `X-Forwarded-For`를 읽지 않으므로, 공개 포트 앞에 프록시를 두려면 ADR 0021을 다시 본다. 봇 판정은 User-Agent만 본다.
- 보존 기간 파기는 DB 행만 익명화한다. 백업·내려받은 CSV의 파기, 감사 로그 자체의 보존 기간, 신청자별 즉시 파기(동의 철회)는 없다(ADR 0020). 애플리케이션 로그에 남는 가린 조각(가린 전화번호·이메일)도 파기 대상이 아니며 로그 보존 기간으로 관리해야 한다.
- 지표 쿼리는 이번 규모에 맞춰 단순하게 두었다. 데이터가 커지면 visits 파티셔닝과 집계 테이블을 검토한다(ROADMAP).

## 그 뒤 (ROADMAP)

Spring Authorization Server와 Google SSO, lead.created 웹훅, 일별 추이와 QR 코드, 폼 A/B, Gradle 멀티모듈. 배포는 위의 v0.5.0 후보다. 자세한 내용은 [ROADMAP.md](ROADMAP.md).
