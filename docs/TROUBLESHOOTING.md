# 문제 해결

증상별로 원인과 해결 방법을 정리했다. API 오류는 모두 ProblemDetail(`application/problem+json`) 형식이고 `code` 값으로 원인을 구분한다.

## 1. 실행

| 증상 | 원인 | 해결 |
|---|---|---|
| `docker compose up`에서 backend가 바로 종료되고 로그에 `JWT_SECRET` | `.env`의 `JWT_SECRET`이 비었거나 32바이트 미만(자리표시 값 `change-me` 포함) | README의 `sed ... openssl rand -base64 48` 명령으로 `.env`를 다시 만든다 |
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
| `unknown_field` | 템플릿에 없는 이름의 값이 들어옴 | 템플릿을 고친 뒤 새 폼으로 게시 |
| `duplicate_submission` (409) | 같은 폼에 같은 전화번호나 이메일로 이미 신청 | 정상 동작(중복 방지) |
| `forbidden_origin` (403) | `Origin: null`인 요청, 즉 sandbox iframe 안 스크립트가 직접 API를 호출 | 래퍼를 통해 제출한다(업로드 HTML이 직접 fetch하지 않게) |
| `unsupported_media_type` (415) | JSON이 아닌 제출 | `Content-Type: application/json` |
| `payload_too_large` (413) | 제출 본문 64KB 초과 | 긴 입력을 줄인다 |
| value 없는 체크박스 값이 `on`으로 저장된다 | 브라우저 기본값 | 정상 동작. 의미 있는 값을 원하면 HTML에 `value`를 준다 |

## 6. 지표가 예상과 다를 때

- **방문은 늘었는데 방문자는 그대로**: 같은 브라우저가 여러 번 연 것(쿠키 `lm_vid` 기준). 정상.
- **신청은 있는데 전환 방문자가 0**: 그 기간 안에 방문 기록이 없는 신청(쿠키를 막은 브라우저 등)은 전환에 들어가지 않는다.
- **채널별 전환 합계가 캠페인 전체와 다름**: 채널별 전환은 같은 채널 안에서만 센다(ADR 0005).
- **크리에이터 행을 더해도 캠페인 전체와 다름**: 크리에이터별 전환은 같은 크리에이터 안에서만 센다. 한 사람이 크리에이터 S 링크로 보고 B 링크로 신청하면 캠페인 전체에서는 전환이지만 S·B 어느 행에서도 전환이 아니다(ADR 0014).
- **크리에이터 링크로 들어온 신청이 직접·자체 링크 행에 있음**: 크리에이터 링크로 방문한 뒤 공개 주소로 다시 들어와 신청하면 신청은 링크 없이(direct) 기록된다(ADR 0005 알려진 한계).
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

로그에는 전화번호·이메일 원문이 남지 않는다(마스킹). 신청 원문은 관리자 화면의 명단 또는 DB에서만 확인한다. 문제를 공유할 때도 원문 대신 마스킹 값이나 신청 id를 쓴다.
