# 문서

처음 보는 분은 [GitHub 위키](https://github.com/kimcno3/lead-magnet-crm/wiki)부터 보면 된다(무엇을 왜 만들었는지, 직접 실행하는 순서, 화면, 설계 하이라이트 요약). 실행·테스트 방법은 저장소 루트의 [README](../README.md)에 있다.

| 문서 | 내용 |
|---|---|
| [GUIDE.md](GUIDE.md) | 사용 가이드: 화면별 사용법, 개인정보 키 교체(2.11), AI로 신청 폼 HTML 만들 때의 조건, 유스케이스 |
| [AI_FORM_PROMPT.md](AI_FORM_PROMPT.md) | AI에게 신청 폼 HTML을 만들게 할 때 붙여 넣는 프롬프트, 규칙별 검사 코드 대응표, 통과하는 예시 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 문제 해결: 실행·로그인·업로드·신청·지표 증상별 원인과 해결, 오류 코드표 |
| [WORKPLAN.md](WORKPLAN.md) | 작업 계획과 진행 현황, 다음 작업 |
| [RELEASE_NOTES_v0.4.0.md](RELEASE_NOTES_v0.4.0.md) | v0.4.0 릴리스 노트: 기능, v0.3.0에서 올리는 방법(DB 백업·다운그레이드 금지·키 교체 시 `docker compose up -d`) |
| [RELEASE_NOTES_v0.3.0.md](RELEASE_NOTES_v0.3.0.md) | v0.3.0 릴리스 노트: 기능, v0.2.0에서 올리는 방법(네트워크 재생성·루프백 포트·TRUSTED_PROXIES) |
| [RELEASE_NOTES_v0.2.0.md](RELEASE_NOTES_v0.2.0.md) | v0.2.0 릴리스 노트: 기능, v0.1.0에서 올리는 방법, 필수 키 |
| [OVERVIEW.md](OVERVIEW.md) | 설계 개요: 아키텍처, 보안 설계, 테스트 전략 |
| [PLAN.md](PLAN.md) | 요구사항 해석과 초기 계획 |
| [adr/](adr/README.md) | 결정 기록(ADR) |
| [api/openapi.yaml](api/openapi.yaml) | API 명세 |
| [e2e/](e2e/results.json) | 브라우저 E2E 결과와 스크린샷 |

## 설계와 결정 (핵심 ADR)

가장 중요하게 본 요구는 "등록한 HTML은 관리자 인증 정보와 관리자 API에 접근할 수 없어야 한다"와 "인증한 운영자만 신청자 데이터에 접근한다"입니다. 핵심 결정은 다음과 같습니다. 전체 23건은 [ADR 목록](adr/README.md), 한 줄 요약은 [위키의 결정 기록 요약](https://github.com/kimcno3/lead-magnet-crm/wiki/결정-기록-요약)에 있습니다.

| 결정 | 이유 | ADR |
|---|---|---|
| 앱 하나에 포트 두 개(관리자 :8080, 공개 :8081) | 앱을 쪼개지 않고 출처(origin) 단위로 관리자와 공개 영역을 격리 | [0010](adr/0010-single-app-two-ports.md) |
| 업로드 HTML은 sandbox iframe + postMessage + CSP | AI가 만든 폼의 스크립트는 살리고 권한은 0으로 | [0002](adr/0002-html-isolation.md) |
| access token은 메모리, refresh는 HttpOnly 쿠키로 매번 교체·재사용 감지 | 스크립트 주입으로 토큰을 훔칠 곳을 없앰 | [0003](adr/0003-auth.md) |
| 전환율 = 방문과 신청 양쪽에 있는 방문자 ÷ 방문자(서울 시간) | 모호한 "전환율"을 재현 가능한 정의로 | [0005](adr/0005-metrics-definition.md) |
| 링크로 채널·크리에이터 귀속 | 성과형(R/S) 협업의 정산 근거 | [0006](adr/0006-channel-attribution.md), [0014](adr/0014-creator-attribution.md) |
| 연락처 AES-256-GCM 암호화, 중복 판정은 HMAC, 원문 열람은 감사 필수 | DB가 새어도 연락처 보호, 기록 없는 열람 금지 | [0015](adr/0015-pii-encryption.md), [0016](adr/0016-audit-log.md) |
| 보존 기간이 지나면 연락처만 지우고 지표는 유지 | 개인정보 최소 보관과 성과 기록 보존을 함께 | [0020](adr/0020-retention-and-consent-evidence.md) |

API 명세는 [api/openapi.yaml](api/openapi.yaml)(실행 중이면 http://localhost:8080/swagger-ui.html)에 있습니다.
