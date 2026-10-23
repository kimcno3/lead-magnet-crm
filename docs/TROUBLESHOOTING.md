# 문제 해결

증상별로 원인과 해결 방법을 정리했다. API 오류는 모두 ProblemDetail(`application/problem+json`) 형식이고 `code` 값으로 원인을 구분한다.

## 1. 실행

| 증상 | 원인 | 해결 |
|---|---|---|
| `docker compose up`에서 backend가 바로 종료되고 로그에 `JWT_SECRET` | `.env`의 `JWT_SECRET`이 비었거나 32바이트 미만(자리표시 값 `change-me` 포함) | README의 `sed ... openssl rand -base64 48` 명령으로 `.env`를 다시 만든다 |
| backend 로그에 `JWT_SECRET은 LEAD_ENC_KEY·LEAD_HMAC_KEY·FORM_TOKEN_KEY와 달라야 합니다` | 같은 값을 여러 키에 붙여 넣음 | 네 키를 각각 따로 생성한다(README의 `.env` 생성 명령은 서로 다른 값을 만든다) |
| backend가 바로 종료되고 로그에 `LEAD_ENC_KEY` 또는 `LEAD_HMAC_KEY` | 값이 없거나 base64 32바이트가 아님(자리표시 값 `change-me` 포함), 또는 두 키가 같음 | 각각 `openssl rand -base64 32`로 만들어 `.env`에 넣는다(README의 `sed` 명령이 함께 만든다). **이미 신청이 쌓인 DB라면 원래 키를 되살려야 한다** |
| backend가 바로 종료되고 로그에 `LEAD_ENC_KEY가 기존 데이터를 암호화한 키와 다릅니다` 또는 `LEAD_HMAC_KEY가 기존 데이터의 중복 판정 해시를 만든 키와 다릅니다` | 키 일치 확인(`pii_key_check`)에서 현재 키가 처음 데이터를 쓴 키와 다름. `.env`를 다시 만들었거나(README의 `sed` 명령은 키를 새로 만든다) 키를 바꿈 | 원래 키로 되돌린다. 키를 잃었다면 기존 신청자 정보는 복구할 수 없다. 로컬 개발이라면 `docker compose down -v`로 DB를 지우고 다시 띄운다(데이터 모두 삭제). 키를 바꿔야 하면 ROADMAP의 "PII 키 교체 절차"가 필요하다(아직 없음, ADR 0015) |
| backend가 마이그레이션 중 멈추고 로그에 `V5 중단: 암호문·해시가 채워지지 않은 leads 행이 있습니다` | 백필되지 않은 행이 있어 V5가 중단됨. 평문 컬럼을 지우면 그 행의 연락처가 사라지므로 가드가 막은 것(ADR 0015). Flyway는 실패한 V5를 기록하지 않는다 | v0.1.0에서 올리는 DB에 신청이 이미 있을 때 나는 오류다(V4·V5가 한 번에 적용되고, 백필하던 러너(#42)는 v0.2.0에 없다). 길은 둘이다. (a) 데이터가 필요 없으면 `docker compose down -v`로 새 DB에서 시작한다(데이터 모두 삭제). (b) 데이터를 지키려면 커밋 `8b318db`(PR #42)를 체크아웃해 같은 `.env`(같은 `LEAD_ENC_KEY`·`LEAD_HMAC_KEY`)로 한 번 기동해 백필시키고, ADR 0015의 게이트 쿼리가 `0`인지 확인한 뒤 v0.2.0을 체크아웃해 기동한다. 자세한 절차는 [OVERVIEW 10절](OVERVIEW.md#10-v020-extras) |
| backend 로그에 `Migration checksum mismatch for migration version 4` | 리뷰 중 바뀌기 전의 V4 초안을 적용한 로컬 DB(공유 환경에는 없음) | `docker compose down -v`로 로컬 DB를 지우고 다시 띄운다 |
| 명단의 한 줄이 "복호화 불가", 그 신청의 원문 조회가 500 `internal_error`, 로그에 `leadId=` | 그 행의 암호문이 손상됨(직접 수정, 다른 컬럼 값 복사, 변조) | 해당 신청 id의 행을 확인한다. 로그에는 id만 남고 원문은 남지 않는다. 명단의 다른 줄은 정상으로 보인다 |
| backend가 바로 종료되고 로그에 `FORM_TOKEN_KEY` | 공개 폼 작성 시간 토큰 서명 키가 없거나 base64 32바이트가 아님(자리표시 값 `change-me` 포함) | `openssl rand -base64 32`로 만들어 `.env`에 넣는다(README의 `sed` 명령이 함께 만든다). 바꾸면 열려 있던 공개 폼 페이지의 토큰만 무효가 된다(새로 고치면 된다, ADR 0019) |
| backend 로그에 `ADMIN_SEED_EMAIL과 ADMIN_SEED_PASSWORD는 함께 설정해야 합니다` | 둘 중 하나만 설정 | 둘 다 채우거나 둘 다 비운다 |
| backend 로그에 `ADMIN_SEED_PASSWORD는 12자 이상` | 시드 비밀번호가 짧음 | 12자 이상, UTF-8 72바이트 이하로 바꾼다 |
| `.env`의 비밀번호를 바꿨는데 로그인이 안 된다 | 시드는 계정이 **없을 때만** 만든다. 이미 있는 계정은 바꾸지 않는다(ADR 0009) | `docker compose down -v`로 DB를 지우고 다시 띄운다(데이터 모두 삭제) |
| `port is already allocated` | 5173, 8080, 8081, 5432 중 사용 중인 포트가 있음 | `.env`의 `ADMIN_WEB_PORT`, `ADMIN_PORT`, `PUBLIC_PORT`, `POSTGRES_PORT`를 바꾼다. 포트를 바꾸면 `ADMIN_ORIGIN`, `PUBLIC_BASE_URL`도 맞춘다 |
| 관리자 화면이 "서버에 연결하지 못했습니다" | backend가 아직 기동 중이거나 멈춤 | `docker compose ps`에서 backend가 `healthy`인지 확인, `docker compose logs backend` |

## 2. 로그인과 세션

| 증상 | 원인 | 해결 |
|---|---|---|
| "이메일 또는 비밀번호가 올바르지 않습니다" (`invalid_credentials`) | 계정 정보 불일치. 없는 이메일도 같은 문구로 답한다 | `.env`의 시드 값 확인. 바꿨다면 위 "로그인이 안 된다" 참고 |
| 갑자기 로그인 화면으로 돌아간다 | refresh 토큰 만료(14일 미사용) 또는 재사용 감지(`refresh_token_reused`)로 세션 전체 폐기 | 다시 로그인한다. 같은 브라우저를 여러 탭에서 동시에 오래 열어 두었다면 재사용으로 판단될 수 있다(ADR 0003) |
| 다른 포트·주소로 관리자 화면을 띄웠더니 API가 403 | 관리자 API CORS는 `ADMIN_ORIGIN` 하나만 허용 | `.env`의 `ADMIN_ORIGIN`을 실제 관리자 화면 주소로 맞춘다 |

## 3. 템플릿 업로드 (오류 코드별)

| code | 뜻 | 해결 |
|---|---|---|
| `template_empty` | 빈 파일 | 내용이 있는 HTML을 올린다 |
| `template_invalid_extension` | `.html`/`.htm`이 아님 | 확장자를 확인한다 |
| `template_not_utf8` | UTF-8이 아니거나 NUL 문자가 있음 | 편집기에서 UTF-8로 다시 저장한다 |
| `template_too_large`, `payload_too_large` (413) | 512KB 초과 | 이미지를 줄이거나 외부 리소스를 빼서 용량을 줄인다 |
| `template_form_count_invalid` | `<form>`이 없거나 2개 이상 | form을 정확히 하나로 |
| `template_forbidden_field` | `type="file"` 또는 `type="password"` 입력 | 해당 입력을 지운다 |
| `template_field_invalid` | 필드 이름 규칙 위반, 51개 이상, radio/checkbox가 아닌데 같은 이름 중복 | 이름은 영문자 시작, 영문·숫자·`_`, 50자 이하. 필드 50개 이하 |
| `template_contact_field_missing` | `phone` 또는 `email` 이름의 입력이 없음(또는 select·radio·checkbox로 되어 있음) | `name="phone"`(텍스트·tel) 또는 `name="email"` 입력을 추가 |

### 3.1 검사 경고 (업로드는 되지만 확인이 필요한 것)

업로드 다이얼로그의 **검사만 하기**와 템플릿 상세의 검사 결과에 나온다. 경고와 참고는 업로드를 막지 않는다([ADR 0017](adr/0017-template-inspection.md)). 처음부터 경고 없이 만들려면 [AI 폼 프롬프트](AI_FORM_PROMPT.md)를 쓴다.

| code | 종류 | 뜻 | 해결 |
|---|---|---|---|
| `external_script` | 경고 | `<script src="https://...">`는 공개 폼의 CSP(`script-src 'self' 'unsafe-inline'`)에 막혀 실행되지 않는다 | 필요한 코드를 `<script>` 안에 직접 넣는다. 라이브러리가 꼭 필요하면 소스를 붙여 넣는다 |
| `external_stylesheet` | 경고 | 외부 CSS(`<link rel=stylesheet>`, `@import`, Tailwind·Bootstrap CDN)가 막혀 스타일 없이 보인다 | CSS를 `<style>` 안에 직접 쓴다 |
| `external_image` | 경고 | 외부 이미지·아이콘(`<img src>`, `srcset`, `poster`, CSS `url()`, `<link rel=icon>`)이 표시되지 않는다 | 이미지를 `data:` URI로 넣거나(합계 200KB 이하) 빼고 CSS 그라데이션·이모지로 대신한다 |
| `external_font` | 경고 | 웹폰트(Google Fonts, `@font-face`의 외부 url)가 막혀 시스템 폰트로 보인다 | `font-family: system-ui, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`를 쓴다 |
| `large_inline_image` | 경고 | `data:` URI 합계가 200KB를 넘는다. 템플릿 용량 한도(512KB)에 가까워지고 폼이 느리게 열린다 | 이미지를 줄이거나(압축, 작은 해상도) 뺀다 |
| `missing_label` | 경고 | 라벨이 없는 입력이다. 화면 읽기 도구가 읽지 못하고 필드 라벨이 `name`이나 placeholder로 대체된다 | `<label for="id">`를 연결하거나 입력을 `<label>`로 감싼다 |
| `contact_not_required` | 경고 | `phone`·`email` 중 `required`인 것이 없다. 방문자가 연락처 없이 제출하면 신청이 거절된다 | 연락처 입력 하나 이상에 `required`를 붙인다 |
| `form_action_external` | 참고 | form `action`이 외부 주소다. 공개 폼은 래퍼가 제출을 받으므로 `action`은 쓰지 않는다 | 지워도 된다. 그대로 두어도 동작은 같다 |
| `fetch_in_script` | 참고 | 스크립트에 `fetch`·`XMLHttpRequest`·`sendBeacon`·`WebSocket`·`EventSource`가 있다. 공개 폼은 `connect-src 'self'`라 외부로 나가지 못하고 신청 제출은 래퍼가 대신 보낸다 | 그 호출에 기대는 동작(외부 API로 우편번호 조회 등)은 빼거나 입력으로 바꾼다. 단순 검사 토큰 스캔이라 주석·문자열 속 단어도 잡힌다 |
| `consent_in_html` | 참고 | 동의 체크박스로 보이는 입력이다. 개인정보 수집 동의는 폼을 만들 때 입력하는 동의 문구로 래퍼가 따로 받는다 | 개인정보 동의용이면 지운다(이름에 `agree`·`consent`·`privacy`, 라벨에 동의·개인정보·약관이 있으면 감지한다). 마케팅 수신 같은 선택 항목이면 그대로 둬도 된다(일반 선택 값으로 저장된다) |

참고: 한 코드의 경고가 20건을 넘으면 20건만 보이고 "외 N건" 한 줄이 붙는다(전체 최대 100건). 같은 종류를 하나 고치면 대부분 함께 사라진다.

참고: 검사 결과는 업로드 시점의 값이다. 이 기능 이전에 올린 템플릿은 상세를 열 때마다 계산한다.

### 3.2 새 버전 업로드 (PUT)

템플릿 상세의 **새 버전 업로드**는 업로드와 같은 검사를 하므로 위 오류 코드가 그대로 나온다. 실패하면 템플릿과 초안 폼은 **전혀 바뀌지 않는다**(ADR 0018).

| 증상 | 원인 | 해결 |
|---|---|---|
| 새 버전을 올렸는데 공개 폼이 그대로다 | 게시한 폼은 게시 시점의 HTML·필드를 쓴다 | 정상 동작. 반영하려면 새 폼을 만들어 게시한다 |
| 폼 패널에 "게시 시점 템플릿과 현재 템플릿이 다릅니다" | 게시한 뒤 템플릿에 새 버전이 올라옴 | 정상 안내. 공개 폼은 게시 당시 내용이다 |
| 새 버전 뒤 초안 폼의 라벨이 새 템플릿 값이다 | 직접 바꾼 라벨만 유지되고, 바꾸지 않았거나 원래 값으로 되돌린 라벨은 새 템플릿을 따른다 | 필요하면 초안에서 다시 고친다 |
| 새 버전 업로드가 잠시 늦다 | 같은 템플릿 폼을 게시·수정하는 중이면 끝날 때까지 기다린다 | 잠시 뒤 완료된다 |

## 4. 캠페인·폼·링크

| code | 뜻 | 해결 |
|---|---|---|
| `campaign_archived` | 보관된 캠페인에 폼을 만들려 함 | 새 캠페인을 만든다(보관은 되돌릴 수 없다) |
| `form_not_editable` | 게시·마감된 폼의 제목·동의 문구·필드 설정 수정 | 초안에서만 수정 가능. 바꿔야 하면 새 폼을 만든다 |
| `invalid_form_transition` | 초안→마감, 마감→게시 같은 허용되지 않은 상태 변경 | 초안 → 게시 → 마감 순서만 가능 |
| `slug_taken` | 같은 slug가 이미 있음 | 다른 slug를 쓰거나 비워서 자동 생성 |
| `link_exists` | 같은 폼·채널·크리에이터(없음 포함) 링크가 이미 있음 | 기존 링크를 복사해 쓴다(폼 × 채널 × 크리에이터마다 1개, ADR 0014) |
| `creator_archived` | 보관된 크리에이터로 새 링크를 만들려 함 | 보관은 되돌릴 수 없다. 이미 만든 링크는 계속 동작한다. 새로 협업하면 크리에이터를 다시 등록한다 |
| `creator_already_archived` | 이미 보관된 크리에이터를 다시 보관 | 정상 동작. 상태는 그대로다 |
| `not_found` (링크 만들기) | 없는 폼이나 없는 `creatorId` | 크리에이터 목록을 새로 불러와 다시 고른다 |
| `form_closed` | 마감된 폼에 링크를 만들려 함 | 마감 폼은 새 링크를 만들 수 없다 |
| `validation_failed` | 입력 형식 오류. `errors[]`에 필드별 사유 | 화면의 필드 옆 문구를 따른다 |

## 5. 공개 폼과 신청

| 증상 / code | 원인 | 해결 |
|---|---|---|
| 공개 폼이 "신청 폼을 찾을 수 없습니다"(404) | slug가 틀렸거나 폼이 아직 초안 | 관리자 화면에서 **게시**했는지 확인 |
| "마감된 신청입니다"(410) | 폼이 마감됨 | 정상 동작 |
| 업로드한 HTML의 이미지·웹폰트가 공개 폼에서 안 보인다 | 공개 폼 CSP가 외부 이미지·폰트·CSS를 막는다(방문자 입력 유출 방지, ADR 0002) | 이미지는 `data:` URI로, 폰트는 시스템 폰트로 |
| 관리자 미리보기에서 HTML의 스크립트가 안 돈다 | 관리자 화면 CSP가 업로드 스크립트를 막는다(ADR 0011) | 게시 후 공개 URL에서 동작을 확인 |
| `consent_required` | 동의 체크박스 미체크 | 폼 아래 동의 체크 |
| `contact_required` | 전화번호·이메일 둘 다 비어 있음 | 둘 중 하나 이상 입력 |
| `required`, `invalid_format`, `invalid_option`, `too_long` | 필수 누락, 이메일·휴대전화 형식 오류(010 등으로 시작하는 10~11자리), 선택지 밖 값, 1000자 초과 | 표시된 항목을 고친다 |
| `unknown_field` | 게시된 폼의 필드 기준(게시 시점 스냅샷)에 없는 이름의 값이 들어옴. 템플릿에 새 버전을 올려도 이미 게시한 폼의 기준은 바뀌지 않는다 | 템플릿을 고친 뒤 새 폼으로 게시 |
| `duplicate_submission` (409) | 같은 폼에 같은 전화번호나 이메일로 이미 신청 | 정상 동작(중복 방지) |
| `forbidden_origin` (403) | `Origin: null`인 요청, 즉 sandbox iframe 안 스크립트가 직접 API를 호출 | 래퍼를 통해 제출한다(업로드 HTML이 직접 fetch하지 않게) |
| `unsupported_media_type` (415) | JSON이 아닌 제출 | `Content-Type: application/json` |
| `payload_too_large` (413) | 제출 본문 64KB 초과 | 긴 입력을 줄인다 |
| `invalid_form_token` (422), "잠시 후 다시 제출해 주세요" | 폼을 연 지 2초가 안 돼 제출했거나, 24시간 넘게 열어 둔 페이지, 또는 래퍼를 거치지 않고 `ft` 없이 API를 부름(ADR 0019) | 래퍼가 토큰을 새로 받아 두므로 잠시 뒤 다시 제출한다. 계속되면 페이지를 새로 고친다. 직접 호출이면 `GET /api/public/forms/{slug}`의 `ft`를 2초 뒤에 실어 보낸다 |
| `too_many_requests` (429), `Retry-After` 헤더 | 같은 IP에서 분당 10회, 같은 방문자 쿠키로 분당 5회 넘게 제출(ADR 0019). 리버스 프록시 뒤라면 모든 요청이 프록시 IP 하나로 보여 쉽게 걸린다 | `Retry-After` 초만큼 기다린다. 한도는 `app.public.rate-limit.*`로 바꾼다. 프록시 뒤 배포는 `server.forward-headers-strategy`와 신뢰 프록시를 설정한다 |
| 신청이 "접수되었습니다"인데 명단에 없다 | 래퍼가 폼에 몰래 넣는 honeypot 입력 `_lm_trap`(화면 밖, `autocomplete=off`, 인라인 `!important` 스타일)이 채워진 제출은 봇으로 보고 201만 주고 저장하지 않는다(ADR 0019). 업로드 HTML의 스크립트나 CSS는 같은 문서라 이 입력을 볼 수 있어, 템플릿 스크립트가 모든 입력에 값을 넣거나 인라인 스타일을 걷어 드러냈을 수 있다(CSS만으로는 드러낼 수 없다) | backend 로그의 `honeypot 제출 무시` 건수를 확인한다(입력값은 남기지 않는다). 템플릿 스크립트가 `form` 안의 모든 `input`을 채우지 않는지, `_lm_trap`을 건드리지 않는지 확인한다 |
| value 없는 체크박스 값이 `on`으로 저장된다 | 브라우저 기본값 | 정상 동작. 의미 있는 값을 원하면 HTML에 `value`를 준다 |

## 6. 지표가 예상과 다를 때

- **방문은 늘었는데 방문자는 그대로**: 같은 브라우저가 여러 번 연 것(쿠키 `lm_vid` 기준). 정상.
- **신청은 있는데 전환 방문자가 0**: 그 기간 안에 방문 기록이 없는 신청(쿠키를 막은 브라우저 등)은 전환에 들어가지 않는다.
- **채널별 전환 합계가 캠페인 전체와 다름**: 채널별 전환은 같은 채널 안에서만 센다(ADR 0005).
- **크리에이터 행을 더해도 캠페인 전체와 다름**: 크리에이터별 전환은 같은 크리에이터 안에서만 센다. 한 사람이 크리에이터 S 링크로 보고 B 링크로 신청하면 캠페인 전체에서는 전환이지만 S·B 어느 행에서도 전환이 아니다(ADR 0014).
- **크리에이터 링크로 들어온 신청이 직접·자체 링크 행에 있음**: 크리에이터 링크로 방문한 뒤 공개 주소로 다시 들어와 신청하면 신청은 링크 없이(direct) 기록된다(ADR 0005 알려진 한계).
- **방문이 기대보다 적음(링크를 메신저에 붙였는데 미리보기 방문이 안 잡힘)**: User-Agent가 봇 패턴(facebookexternalhit, kakaotalk-scrap, slackbot, Googlebot, headless, curl 등)이거나 비어 있는 방문은 `visits.is_bot = true`로 남고 모든 지표에서 빠진다(ADR 0005 변경 기록, ADR 0019). 확인: `SELECT is_bot, count(*) FROM visits GROUP BY 1`. 패턴은 `app.tracking.bot-user-agents`로 바꾼다. 신청은 봇 UA여도 센다.
- **`/l/{code}`로 들어왔는데 방문이 두 번 잡히지 않음**: 방문은 공개 폼이 열릴 때 한 번만 기록한다(ADR 0006).
- **날짜 경계**: 기간 필터는 서울 시간 자정 기준이다.

## 7. 테스트

| 증상 | 해결 |
|---|---|
| backend 테스트가 `Could not find a valid Docker environment` | Docker Desktop을 켠다(Testcontainers가 PostgreSQL을 띄운다) |
| `./gradlew` 실행 시 JDK 오류 | JDK 17 이상으로 실행한다. 빌드용 JDK 21은 Gradle이 자동으로 받는다. 여러 JDK가 있으면 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test` |
| `OpenApiExportTest`가 verify 모드에서 실패 | API를 바꾼 뒤 명세를 다시 만들지 않음. `cd backend && ./gradlew test --tests '*OpenApiExportTest'`로 `docs/api/openapi.yaml`을 다시 만들어 커밋 |
| E2E가 "닿지 못했습니다"로 시작 전에 실패 | `docker compose up --build -d`로 전체 시스템을 먼저 띄운다 |
| macOS에서 "다른 앱의 데이터에 접근" 팝업이 반복 | Docker·Chromium을 띄우는 프로세스에 macOS가 묻는 것. 시스템 설정 → 개인정보 보호 및 보안 → 전체 디스크 접근 권한에서 해당 터미널(또는 실행 도구)을 허용 |

## 8. 로그와 개인정보

로그에는 이름·전화번호·이메일 원문이 남지 않는다(마스킹). 신청 원문은 관리자 화면 명단의 **원문 보기**나 **CSV 다운로드(원문)**에서만 확인한다(DB에는 암호문 `*_enc`와 해시만 있고 평문 컬럼은 V5에서 지웠다, ADR 0015). 문제를 공유할 때도 원문 대신 마스킹 값이나 신청 id를 쓴다.

## 9. CSV 다운로드와 감사 로그

| 증상 | 원인 | 해결 |
|---|---|---|
| 원문 보기나 CSV 다운로드가 "서버 오류가 발생했습니다"(`internal_error`)로 실패하고 backend 로그에 `처리하지 못한 예외 … DataAccess…`가 있다 | 감사 로그를 저장하지 못했다(DB 연결·디스크 등). 원문은 기록 없이 내주지 않는다(ADR 0016) | DB 상태를 확인하고 다시 시도한다. 명단 조회는 그대로 된다 |
| backend 로그에 `감사 로그 기록 실패(요청은 계속 처리): action=lead.list` | 명단 조회·로그인 기록을 저장하지 못했다. 응답은 정상으로 나갔다 | DB 상태를 확인한다. 이 기간의 조회 기록은 비어 있다 |
| 엑셀에서 한글이 깨진다 | 파일을 다른 프로그램으로 다시 저장하며 BOM이 빠졌다 | 내려받은 파일을 그대로 연다. 구글 시트는 "가져오기"에서 UTF-8을 고른다 |
| 이름 등 값 앞에 `'`가 보인다 | 앞 공백을 건너뛴 첫 글자가 `=`·`+`·`-`·`@`이거나 탭·CR로 시작하는 값은 스프레드시트가 수식으로 실행하지 않도록 `'`를 붙인다(CSV 수식 주입 방지, ADR 0016) | 정상이다. 엑셀에서 셀에 직접 `'`를 입력할 때와 달리, CSV로 연 `'`는 값의 일부로 들어가 셀에 그대로 보인다. 지우면 그 값이 수식으로 다시 실행될 수 있으니 신청자가 실제로 그렇게 입력한 값인지 확인한 뒤에만 지운다 |
| 연락처 칸이 "복호화 불가" | 그 신청의 암호문이 손상됐거나 키가 다르다(5절 참고) | 명단 화면의 "복호화 불가"와 같은 원인이다 |
| 감사 로그의 IP가 모두 같은 주소(프록시·로드밸런서 주소)다 | IP는 접속한 주소(`getRemoteAddr`)만 기록하고 `X-Forwarded-For`는 위조될 수 있어 읽지 않는다 | 배포 시 신뢰 프록시 설정과 함께 바꾼다(ADR 0016, ROADMAP) |
| 감사 로그 화면에서 `validation_failed` | 모르는 action, 시작일이 종료일보다 늦음, 잘못된 날짜 | 필터를 지우고 다시 고른다 |

## 10. 보존 기간 파기와 동의 문구

| 증상 | 원인 | 해결 |
|---|---|---|
| 원문 보기가 "보존 기간이 지나 연락처를 파기한 신청입니다"(410 `lead_anonymized`) | 캠페인의 보존 기간이 지나 이름·연락처를 지웠다(ADR 0020). 되돌릴 수 없다 | 정상이다. 명단·CSV에도 "파기됨"으로 보인다. 지표는 그대로다 |
| 캠페인 생성·보존 기간 변경이 `validation_failed`(field `retentionDays`) | 보존 기간은 1~3650일 정수다 | 범위 안의 값을 넣는다 |
| 03:00이 지났는데 파기되지 않았다 | 정확히 보존 기간이 된 신청은 아직 남는다(지나야 파기). 또는 `RETENTION_ENABLED=false` | 다음 날 실행을 기다리거나 `POST /api/admin/retention/run?dryRun=true`로 대상 건수를 보고 수동 실행한다 |
| 수동 실행이 `{"anonymized":0, "interrupted":true}`인데 대상이 있었다 | 그 순간 다른 실행(다른 인스턴스의 03:00 실행 등)이 배치를 처리 중이라 이번 실행은 멈췄다. 남은 행은 그 실행이 처리한다 | 감사 로그의 보존 기간 파기 기록에서 다른 실행의 건수를 보거나, 끝난 뒤 다시 실행한다(멱등) |
| backend 로그에 `보존 기간 파기 실패: 원인=…`, 또는 수동 실행이 500 | 실행 중 배치가 실패했다(DB 오류 등). 그 전에 커밋된 배치는 파기된 채로 남고, 감사 로그 **보존 기간 파기**에 그때까지의 건수와 "실패로 중단"·오류 종류가 한 줄 남는다 | DB 상태를 확인한다. 다음 날 다시 돌며 남은 행을 처리한다(멱등). 바로 처리하려면 수동 실행한다. 감사 기록조차 없는 경우는 프로세스가 죽었을 때뿐이며, 그때도 행마다 `anonymized_at`이 남는다 |
| 파기된 사람이 같은 폼에 다시 신청됐다(409가 아니다) | 파기 때 중복 판정용 해시도 지운다(ADR 0007 변경 기록) | 정상이다. 새 신청으로 센다 |
| 동의 문구 보기에 "저장된 동의 문구가 없습니다" | 기능 도입(V10) 전 신청이고 그때 폼 문구로도 채우지 못했다(롤링 배포 중 옛 버전이 받은 신청 등) | 그 신청의 폼 동의 문구(폼 탭)를 참고한다. 백필은 최선이며 보장하지 않는다 |

