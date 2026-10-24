# 0011. 관리자 UI

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

운영자는 관리자 화면에서 로그인하고, AI로 만든 HTML 폼을 올리고, 캠페인·폼·채널별 배포 링크를 만들고, 지표와 명단을 본다. 화면은 백엔드 admin 출처 API(`/api/admin/**`)만 쓴다. 제약은 다음과 같다.

- access token은 메모리에만 두고 Web Storage·IndexedDB·쿠키에 쓰지 않는다. refresh token은 HttpOnly·SameSite=Strict·Path=/api/admin/auth 쿠키다(ADR 0003).
- 업로드 HTML은 신뢰할 수 없다. 관리자 화면에서 미리보기를 보여줄 때도 관리자 출처의 토큰·API에 닿으면 안 된다(ADR 0002).
- 매일 쓰는 내부 운영 도구다. 표와 숫자를 빠르게 읽는 것이 장식보다 중요하다.

## 결정

1. **스택**: React 19 + Vite + TypeScript(strict), 라우팅은 `react-router` 7(`BrowserRouter`). UI 라이브러리·CSS 프레임워크·차트 라이브러리를 쓰지 않고 CSS 변수 토큰과 CSS 파일 세 개(`tokens`, `base`, `components`)로 스타일한다. 데이터 로딩은 작은 `useAsync` 훅으로 충분하다.
2. **같은 출처 프록시**: 운영은 nginx, 개발은 Vite가 `/api/admin/`을 backend admin 포트로 프록시한다. 브라우저는 admin-web 출처 하나만 보므로 CORS가 필요 없고 refresh 쿠키가 그대로 실린다.
3. **토큰**: `api/client.ts` 모듈 변수에만 둔다. 앱 시작 시 `POST /api/admin/auth/refresh`로 세션을 복원한다. API가 401이면 refresh를 한 번만 하고 원 요청을 다시 보낸다. 동시에 난 401은 진행 중인 refresh 하나를 기다리고, 요청 사이에 이미 토큰이 바뀌었으면 refresh 없이 다시 보낸다. 재시도도 401이거나 refresh가 실패하면 세션을 끝내고 로그인 화면으로 보낸다. refresh 요청이 서버에 닿지 못한 경우(fetch 자체가 실패)는 세션이 끝났는지 알 수 없으므로 로그아웃으로 보지 않는다: 토큰을 지우지 않고 오류를 던지며, 시작 시 복원이라면 '서버에 연결하지 못했습니다'와 다시 시도를 보여준다. refresh는 됐지만 `/me`가 실패하면 메모리 토큰을 지우고 로그인 화면으로 보낸다. 로그인·refresh·logout 요청에는 `Authorization`을 붙이지 않는다(만료 토큰이 있으면 Resource Server가 401을 낸다). 새로고침하면 메모리 토큰은 사라지지만 refresh 쿠키로 다시 복원된다.
4. **미리보기 격리**: 업로드 HTML은 `<iframe sandbox="allow-scripts allow-forms" srcdoc>`로만 렌더링한다(`components/SandboxPreview.tsx`). `allow-same-origin`, `dangerouslySetInnerHTML`, `src`(blob:·data:)를 쓰지 않는다.
5. **CSP**: 관리자 화면 응답에 다음 CSP를 붙인다(nginx `add_header ... always`, Vite dev `server.headers`). 값의 원천은 `admin-web/csp.ts`이고 `csp.test.ts`가 nginx.conf와 같은지 확인한다.

   `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'`

   - srcdoc 문서는 부모의 CSP를 상속하므로 관리자 출처에서는 업로드 HTML의 스크립트가 실행되지 않는다. 미리보기는 **정적 미리보기**이고(인라인 스타일은 보인다), 화면에 그렇게 안내한다. 동작까지 보려면 폼을 게시한 뒤 공개 URL(:8081)을 연다. sandbox와 CSP가 이중 방어가 된다.
   - Vite dev 서버는 React Fast Refresh 프리앰블을 인라인 스크립트로 넣으므로 개발 때만 고정 nonce(`'nonce-lm-admin-dev'`)를 `script-src`에 더하고 `html.cspNonce`로 Vite가 넣는 스크립트에 붙인다. 빌드 결과물에는 인라인 스크립트가 없어 nonce가 필요 없다.
   - nginx는 `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `X-Frame-Options: DENY`도 붙인다.
6. **공개 URL**: 공개 폼 주소 앞부분은 배포 링크 응답의 `url`에서 얻고(백엔드 `PUBLIC_BASE_URL`이 기준), 링크가 아직 없으면 빌드 시 `VITE_PUBLIC_BASE_URL`(기본 `http://localhost:8081`)을 쓴다. docker compose는 `PUBLIC_BASE_URL`을 같은 이름의 빌드 인자로 넘긴다.
7. **화면 상태는 URL에**: 캠페인 상세의 탭(`?tab=`), 성과 기간(`from`, `to`), 명단 필터·페이지(`form`, `channel`, `page`), 펼친 폼(`open`)을 쿼리에 둔다. 새로고침·공유해도 같은 화면이 나온다.
8. **디자인 방향**: 따뜻한 회색 중립과 강조색 하나(진한 녹색), 반경 4~6px, 표 13px·tabular-nums, KPI 28px. 카드로 감싸지 않고 선과 여백으로 묶는다. `prefers-color-scheme`으로 어두운 테마도 같은 토큰 구조로 제공한다. 상태는 색과 글자를 함께 쓴다. 모든 목록에 빈·로딩(뼈대 행)·오류 상태를 둔다.
9. **오류 표시**: 백엔드 ProblemDetail의 `detail`을 그대로 보여주고, `errors[{field, message}]`는 해당 입력 아래에 붙인 뒤 첫 오류 입력으로 포커스를 옮긴다. 템플릿 업로드 거절은 `code`별 안내 문구(`labels.ts`의 `TEMPLATE_ERROR_GUIDES`)를 detail 위에 더한다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| access token을 sessionStorage에 저장 | 새로고침해도 refresh 요청이 없다 | XSS 한 번이면 토큰이 유출된다 | 보안 불변식 위반(ADR 0003) |
| 미리보기에 `allow-same-origin` 추가 | 스크립트 동작까지 미리보기에서 확인 | 업로드 HTML이 관리자 출처로 실행되어 토큰·API에 접근 | 보안 불변식 위반 |
| 미리보기를 public 출처(:8081) iframe으로 | 스크립트까지 실제처럼 동작 | 초안 폼·업로드 직후 템플릿을 public 출처에 노출하는 새 경로가 필요하고 `frame-src`를 넓혀야 한다 | 공개 경로를 늘리지 않는다(ADR 0010). 동작 확인은 게시 후 공개 URL로 충분 |
| 개발 서버에서 CSP 생략 | 설정이 단순 | 개발 중 CSP 위반을 운영에서야 발견 | 개발과 운영이 같은 정책을 쓰게 하고 차이는 nonce 하나로 한정 |
| UI 컴포넌트 라이브러리(MUI, shadcn 등) | 컴포넌트를 빨리 얻는다 | 의존성과 번들이 커지고 기본 모양이 템플릿처럼 보인다 | 화면 수가 적고 필요한 컴포넌트(버튼, 다이얼로그, 탭, 표)가 단순하다 |
| 데이터 라우터(`createBrowserRouter` loader) | 로딩·오류 경계를 라우터가 관리 | 인증 복원·401 처리와 엮기가 복잡해진다 | `BrowserRouter`와 `useAsync`로 충분 |

## 결과

- 쉬워지는 것: 토큰이 JS 저장소에 남지 않아 XSS 피해 범위가 줄고, 미리보기는 sandbox와 CSP 두 겹으로 막힌다. 화면 상태가 URL에 있어 링크로 공유할 수 있다.
- 어려워지는 것: 새로고침마다 refresh 요청이 한 번 나간다. 관리자 화면 미리보기로는 업로드 HTML의 스크립트 동작을 확인할 수 없다.
- 다시 볼 조건: 운영자가 미리보기에서 스크립트 동작 확인을 강하게 원하면 public 출처의 별도 미리보기 경로를 새 ADR로 검토한다. 운영자가 여러 명이 되면 권한별 화면을 다시 설계한다.

## 변경 (2026-10-23): 흑백 색상 체계

- 저장소 소유자 요청으로 관리자 화면과 공개 폼 래퍼의 색을 블랙/화이트와 회색 단계로만 구성한다. 강조색(녹색)과 상태 색(적갈색)을 없앴다.
- 상태는 색 대신 형태로 구분한다: 게시됨·진행 중은 검정 채움 배지, 초안·보관은 회색 테두리 배지, 마감은 점선 테두리 배지. 위험 행동(보관, 마감)은 테두리형 버튼 + 확인 다이얼로그, 오류는 굵은 왼쪽 선과 굵은 글씨.
- 대비: 본문 라이트 #0a0a0a/#fff 19.8:1, 보조 텍스트 #5a5a5a/#fff 6.9:1, 다크 #a8a8a8/#111 8.0:1. 모두 WCAG AA 이상.
- 색만으로 상태를 구분하지 않는다는 기존 접근성 원칙과 더 잘 맞는다.

### 2026-10-24 변경: 화면 테마, 사이드바 접기, 표 줄바꿈 (#80)

- 화면 테마를 사이드바 아래에서 고른다(시스템 설정·라이트·다크). 시스템 설정은 `data-theme`을 지워 OS 설정을 따르고, 직접 고르면 `<html data-theme>`이 `prefers-color-scheme`보다 우선한다(`styles/tokens.css`).
- 사이드바는 접기 버튼(«/»)으로 접는다. 접으면 버튼만 남은 좁은 띠가 되고 본문이 넓어진다. 좁은 화면(767px 이하)은 기존처럼 위쪽 가로 막대이고 접기가 없다.
- 테마와 접기 상태는 `src/preferences.ts`가 `localStorage`(`lm.theme`, `lm.sidebar-collapsed`)에 기억한다. 토큰·개인정보가 아닌 화면 설정이라 메모리 보관 원칙(access token만 해당)과 충돌하지 않는다. 저장소가 막히면 기본값으로 동작한다.
- 표 셀은 글자 중간에서 끊지 않는다(`overflow-wrap: normal`, 한국어는 어절 단위). 코드·이메일·날짜·마스킹 이름은 한 줄로 두고, 폭이 모자라면 표가 가로로 스크롤된다. 펼친 폼 패널 안의 표는 바깥 고정 레이아웃의 열 폭·줄바꿈 규칙을 물려받지 않는다. 본문 최대 폭은 1440px로 넓혔다.
- 선택지 목록에서 값이 빈 안내 선택지(select의 "선택해 주세요")는 표시하지 않는다.
