# 0010. 단일 앱·포트 분리

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

관리자 API와 공개 폼은 신뢰 수준이 다르다. 공개 폼은 누구나 열고, 운영자가 올린 신뢰할 수 없는 HTML을 띄운다(ADR 0002). 브라우저의 출처 경계로 둘을 나누려면 출처(scheme+host+port)가 달라야 한다. 한편 과제는 `docker compose up` 한 번으로 재현되어야 하고 서비스를 여럿 두면 운영 부담이 커진다.

## 결정

- Spring Boot 앱 하나에 Tomcat 커넥터 두 개를 둔다. 기본 커넥터는 admin 포트(`ADMIN_PORT`, 기본 8080), `PublicPort`가 `WebServerFactoryCustomizer`로 public 커넥터(`PUBLIC_PORT`, 기본 8081)를 붙인다. 테스트는 public 포트를 0(임의)으로 두고 `PublicPort.get()`으로 실제 포트를 읽는다.
- **OriginGuardFilter**: Spring Security 필터 체인보다 앞(`SecurityProperties.DEFAULT_FILTER_ORDER - 10`)에서 `request.getLocalPort()`를 public 커넥터의 실제 포트와 비교한다.
  - public 포트: `/f/**`, `/l/**`, `/api/public/**`만 통과. 관리자 API, 헬스, OpenAPI 문서, Swagger UI, 직접 요청한 `/error` 등 나머지는 404 ProblemDetail.
  - admin 포트: 공개 경로 세 개를 404. 그 밖에는 Security 체인이 판단한다(`/api/admin/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`).
  - 경로는 Tomcat이 디코딩·정규화한 `servletPath + pathInfo`로 본다. `/f/../api/admin/...`, `/f/..;/api/admin/...` 같은 원문은 정규화 후 공개 경로가 아니므로 404다(인코딩된 `/`는 Tomcat이 400으로 거부한다).
  - 403이 아니라 404로 답해 다른 출처의 경로가 있다는 것도 드러내지 않는다.
- **SecurityFilterChain 세 개**: admin(`/api/admin/**`, Bearer JWT Resource Server, CORS는 `ADMIN_ORIGIN`만), public(`/f/**`, `/l/**`, `/api/public/**`, permitAll, STATELESS, CSRF·CORS·Resource Server 없음 → Authorization 헤더를 무시), 기본(헬스·문서만 열고 나머지 denyAll).
- 보안 헤더 필터(ADR 0002)는 OriginGuard보다 먼저 돌아 public 포트의 차단 응답에도 CSP 등을 붙인다.
- 관리자 화면은 admin-web(nginx, 개발 시 Vite)이 제공하고 `/api/admin/**`·`/actuator/health`를 같은 출처로 backend admin 포트에 프록시한다. 브라우저가 보는 관리자 출처는 admin-web(`ADMIN_ORIGIN`, 로컬 `http://localhost:5173`)이고 public 출처(`http://localhost:8081`)와 다르다. public 출처에서 관리자 API로 보내는 CORS 요청은 거부된다.
- 방문자 쿠키 `lm_vid`(`HttpOnly; SameSite=Lax; Path=/; Max-Age=31536000`, Secure는 `COOKIE_SECURE`)는 public 경로(`/f/{slug}`, `/l/{code}`)에서만 발급한다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| 공개용 앱을 별도 서비스로 분리 | 프로세스 수준 격리 | 도메인 코드 공유·배포·compose 구성이 복잡해진다 | 과제 규모에 비해 무겁다 |
| 한 포트에서 경로로만 구분 | 구성이 가장 단순 | 같은 출처라 업로드 HTML 래퍼가 관리자 API와 같은 출처가 된다 | 브라우저 출처 경계를 쓸 수 없다 |
| 리버스 프록시에서만 포트·경로 제한 | 앱 코드 변경 없음 | 프록시 설정이 빠지면 바로 노출, 테스트로 검증하기 어렵다 | 앱 안에서 막고 통합 테스트로 확인한다 |
| Host 헤더로 출처 판단 | 운영 도메인 분리와 자연스럽다 | Host는 클라이언트가 바꿀 수 있다 | 커넥터 로컬 포트는 위조할 수 없다 |

## 결과

- 출처 격리가 앱 안에서 강제되고 실패 케이스(포트를 바꾼 요청, 경로 조작, public 출처의 CORS)를 통합 테스트로 검증한다.
- 쿠키는 포트를 구분하지 않는다. 로컬(`localhost`)에서는 admin-web의 `lm_refresh`가 `localhost:8081`의 `/api/admin/auth` 요청에도 실릴 수 있지만 public 포트는 그 경로를 404로 막고, 업로드 HTML은 sandbox 불투명 출처라 쿠키를 읽지 못한다. 운영 배포에서는 관리자와 공개 출처를 다른 호스트(예: `admin.example.com`, `forms.example.com`)로 두는 것을 전제로 한다.
- 새 공개 경로를 추가하려면 `PublicPaths`와 이 ADR을 함께 바꿔야 한다.
