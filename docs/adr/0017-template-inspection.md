# 0017. 업로드 HTML 검사 리포트와 AI 프롬프트

- 상태: 승인
- 날짜: 2026-10-24

## 맥락

운영자는 AI가 만든 HTML을 올린다. ADR 0002 변경 기록으로 공개 CSP가 외부 이미지·폰트·CSS를 막게 되었고(외부 스크립트는 처음부터 막힘) AI는 CDN과 웹폰트를 즐겨 쓰므로, 업로드는 성공해도 공개 폼은 스타일·이미지가 빠진 채 열린다. 운영자는 이를 게시한 뒤에야 알게 된다. 계약 오류(ADR 0004, 422)만으로는 이런 "올라가지만 깨지는" 문제와 품질 문제(라벨 없음, 연락처 필수 아님)를 알릴 수 없다.

## 결정

**검사기** `template/domain/TemplateInspector`는 Jsoup만 쓰는 순수 클래스다. `inspect(html)`이 경고 목록 `[{code, severity: warning|info, message, location}]`을 돌려주고, 예외를 던지지 않으며 업로드를 막지 않는다. 계약 오류는 기존 `FormContractParser`가 그대로 422로 막는다. 업로드, 저장 없는 `POST /api/admin/templates/inspect`, 이후 새 버전 업로드(#17)가 같은 `TemplateService.inspect(filename, bytes, name)`을 쓴다(확장자·크기·UTF-8·계약 검사 + 경고 계산, 저장 없음).

| code | 종류 | 찾는 것 |
|---|---|---|
| `external_script` | warning | 외부 `<script src>` (`modulepreload`, `preload as=script` 포함) |
| `external_stylesheet` | warning | 외부 `<link rel=stylesheet>`, CSS `@import`, `preload as=style` |
| `external_image` | warning | `img`·`source`·`video`·`audio`·`input[type=image]`의 `src`, 모든 `srcset`, `poster`, `<link rel=icon>` 계열, `iframe`·`embed`의 `src`, `object[data]`, SVG `image`·`use`의 `href`/`xlink:href`, CSS `url()` |
| `external_font` | warning | `@font-face`의 외부 `url()`, 폰트 확장자(`woff/woff2/ttf/otf/eot`) URL, `preload as=font` |
| `large_inline_image` | warning | 위 위치의 `data:` URI 길이 합계가 200KB 초과 |
| `missing_label` | warning | 라벨 없는 필드(`label for`, 감싼 `label`, `aria-label`, `aria-labelledby` 중 어느 것도 없음; hidden·버튼 제외, 같은 name은 첫 입력 기준) |
| `contact_not_required` | warning | 연락처 입력(`phone`·`email`, 선택지 타입 제외)이 있는데 그중 `required`가 하나도 없음 |
| `form_action_external` | info | form `action`이 외부 URL (래퍼가 제출을 받아 무시됨) |
| `fetch_in_script` | info | 인라인 스크립트의 `fetch(`, `XMLHttpRequest`, `sendBeacon`, `WebSocket`, `EventSource` 토큰 (토큰당 한 건) |
| `consent_in_html` | info | 이름이 `agree|consent|privacy`이거나 라벨에 `동의|개인정보|약관`이 있는 체크박스 |

- 외부 URL은 `http:`, `https:`, 프로토콜 상대 `//`로 시작하는 값이다. 상대 경로와 `data:`는 외부가 아니다. CSS는 주석을 지운 뒤 `@import` → `@font-face` 블록 → 나머지 `url()` 순서로 본다. `<style>`과 `style=""` 모두 본다.
- 분류 기준: `@import`는 파일 종류와 무관하게 `external_stylesheet`(구글 폰트 CSS도 스타일시트다), `@font-face`와 폰트 파일 URL은 `external_font`, 나머지 `url()`은 `external_image`. 같은 `(code, location)`은 한 번만 담는다.
- URL 앞의 공백·제어 문자와 안의 탭·개행은 브라우저처럼 무시하고 스킴을 본다(`ht\ttps:` 우회 방지).
- 입력 크기에 비례해 시간이 늘도록, CSS는 정규식 대신 `indexOf` 스캐너로 훑고(닫는 `)`·`}`가 없으면 멈춤) 문서당 CSS·인라인 스크립트 검사량을 512KB로 묶는다. 닫히지 않는 `url(` 12만 개(480KB)가 정규식 역추적으로 약 300초 걸리던 것을 20ms 안으로 줄였고 회귀 테스트가 2초 제한으로 지킨다.
- 결과 크기: 코드당 20건까지만 담고 넘으면 `외 N건` 한 줄(message, location은 빈 문자열)로 요약한다. 전체는 최대 100건이다.
- `fetch_in_script`는 JS가 아닌 `<script type>`(JSON, 템플릿 등; 비어 있음·`text/javascript`·`application/javascript`·`module`만 검사)은 보지 않는다. 의도적으로 단순한 토큰 스캔이다. 주석·문자열 속 단어도 잡히고 난독화된 호출은 놓친다. 보안 경계가 아니라(경계는 CSP `connect-src 'self'`) 운영자에게 주는 힌트이므로 참고(info)로만 낸다. `form_action_external`, `consent_in_html`도 동작에 해가 없어 참고다.
- `contact_not_required`를 "각 연락처 입력이 required"가 아니라 "연락처 입력 중 required가 하나도 없음"으로 정한 이유: 서버는 phone·email 중 하나만 오면 받으므로(`SubmissionValidator`) 이메일이 선택인 흔한 폼에 경고하면 오탐이다.

**저장과 기존 데이터**
- V7이 `html_templates.inspection JSONB`(NULL 허용)를 더한다. 업로드 시점의 경고 목록을 저장해, 운영자가 업로드 때 본 결과를 상세에서도 같게 본다.
- 이 마이그레이션 이전의 행은 SQL로 HTML을 검사할 수 없어 NULL로 남는다. **읽을 때 계산한다**: `TemplateService.inspectionOf`가 NULL이면 `TemplateInspector.inspect(content)`를 돌려주며 **저장하지 않는다**. 기동 시 백필 러너를 두지 않은 이유는 (1) 검사가 빠른 순수 계산이라 읽기 비용이 무시할 만하고, (2) GET이 쓰기를 하지 않으며, (3) 기동 단계·락·부분 실패를 늘리지 않기 위해서다. 대가로 NULL 행은 규칙이 바뀌면 최신 규칙으로 보이고, 새 행은 업로드 시점 규칙으로 고정된다.
- 응답 필드 `inspection`은 경고 배열(없으면 `[]`)이다. 업로드 201과 상세 200에 들어간다. 목록 응답에는 넣지 않는다.

**API**: `POST /api/admin/templates/inspect`(multipart `file`, 선택 `name`)가 `{name, size, fields, inspection}`을 돌려주고 저장하지 않는다. 계약 오류는 업로드와 같은 코드·상태(422, 413)다.

**관리자 화면**: 업로드 다이얼로그에 "검사만 하기"를 두어 결과를 다이얼로그 안에 보여 주고, 템플릿 상세에 검사 결과를 경고와 참고 두 묶음으로 보인다. 흑백 체계(ADR 0011 변경)에 맞춰 색·아이콘 없이 "경고"/"참고" 텍스트 라벨과 구분선으로 나눈다. 템플릿 목록의 "AI 프롬프트 복사"는 프롬프트를 클립보드에 복사한다.

**AI 프롬프트**: `docs/AI_FORM_PROMPT.md`가 운영자용 문서(프롬프트, 규칙-코드 대응표, 통과 예시)이고, 관리자 화면은 같은 글을 `admin-web/src/aiFormPrompt.ts`에서 쓴다. 번들은 `admin-web/` 디렉터리만 Docker 빌드 컨텍스트로 받으므로 문서를 직접 import하지 않고, 두 글이 같은지를 admin-web 테스트가 확인한다. 백엔드 테스트는 문서의 예시 HTML이 계약 오류도 경고도 없는지, 대응표와 TROUBLESHOOTING이 모든 경고 코드를 설명하는지 확인한다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| 외부 리소스가 있으면 업로드 거부(422) | 깨진 폼이 아예 없다 | 외부 리소스가 장식용인 폼까지 막고 운영자가 고칠 수단 없이 막힌다 | 경고로 알리고 선택은 운영자에게 맡긴다 |
| 업로드 시 외부 URL을 자동으로 지우거나 data URI로 바꿈 | 운영자가 할 일이 없다 | 서버가 외부로 요청해야 하고(SSRF), 원문이 바뀐다 | ADR 0004의 "정제하지 않는다"와 어긋난다 |
| 검사 결과를 저장하지 않고 항상 읽을 때 계산 | 컬럼이 필요 없다 | 업로드 때 본 결과와 나중 결과가 규칙 변경에 따라 달라진다 | 업로드 시점 값을 고정해 둔다. 기존 행만 읽을 때 계산한다 |
| 기동 시 백필 러너 | 모든 행이 저장 값을 가진다 | 기동 지연·부분 실패 처리, GET/기동의 쓰기 | 읽기 시 계산이 더 단순하다 |
| 브라우저에서 CSP 위반 이벤트로 감지 | 실제로 막힌 것만 보고한다 | 업로드 시점에 알 수 없고 관리자 미리보기는 스크립트를 실행하지 않는다 | 업로드 시점 정적 검사가 목적에 맞다 |
| 프롬프트를 화면에서 서버 API로 받아옴 | 원천이 하나다 | 전용 API·보안 설정이 늘고 프롬프트는 정적 텍스트다 | 상수 파일 + 일치 테스트로 충분하다 |

## 결과

- 외부 리소스 때문에 깨질 폼을 올리기 전에 알 수 있고, 운영자는 "검사만 하기"를 반복해 고친 뒤 올린다. 경고를 무시한 업로드도 그대로 된다.
- 검사는 정적 문자열 분석이라 스크립트가 만들어 내는 동적 요청(`document.createElement('link')` 등)은 잡지 못한다. 그런 요청은 CSP가 막는다(ADR 0002).
- 새 경고 코드를 더하면 `TemplateInspectorTest`, `docs/AI_FORM_PROMPT.md` 대응표, TROUBLESHOOTING 3.1절을 함께 고친다(문서 테스트가 코드 누락을 잡는다).
- `HtmlTemplate`에 `inspection`이 생겨 새 템플릿 생성 경로(#17 새 버전 업로드)도 `TemplateService.inspect`를 쓰면 같은 값을 얻는다.
