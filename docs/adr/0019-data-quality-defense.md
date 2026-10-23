# 0019. 데이터 품질 방어: 봇 방문 제외, honeypot, 작성 시간 토큰, 제출 rate limit

- 상태: 승인
- 날짜: 2026-10-24

## 맥락

공개 폼은 인증이 없고 링크는 SNS에 퍼진다. 두 가지가 숫자를 흐린다.

- **방문 부풀림**: 링크를 SNS·메신저에 붙이면 미리보기 크롤러(facebookexternalhit, twitterbot, slackbot, kakaotalk-scrap 등)와 검색 크롤러가 `/f/{slug}`를 연다. 지금은 `/f/{slug}` 200마다 방문 한 행이라(ADR 0006) 이 요청이 방문·방문자로 잡혀 전환율이 낮아진다.
- **스팸 신청**: 스크립트가 제출 API를 직접 부르면 명단이 쓰레기로 찬다. 서버가 신뢰 경계이므로(ADR 0007) 래퍼 검증만으로는 막지 못한다.

모호한 점과 가정:

- **봇 판정 기준**: User-Agent만 본다. 위조할 수 있지만 목표는 "정직한 크롤러가 지표를 부풀리지 않게"이지 악성 봇 차단이 아니다(그건 아래 토큰·rate limit 몫).
- **봇 UA 방문의 쿠키와 신청**: 봇으로 판정한 방문에도 응답은 똑같이 준다(쿠키 포함). 같은 UA로 들어온 신청도 UA만으로 막지 않는다(인앱 브라우저 UA를 잘못 잡았을 때 신청을 잃는 쪽이 더 나쁘다).
- **IP**: 배포 환경의 리버스 프록시가 정해지지 않았다(ROADMAP). 감사 로그(ADR 0016)와 같은 이유로 `getRemoteAddr()`만 믿는다.

## 결정

### 봇 방문 표시와 지표 제외

- `V9__visit_bot_flag.sql`: `visits.is_bot BOOLEAN NOT NULL DEFAULT false`. 추가만 하는 변경이고 기존 행은 사람 방문(false)으로 남는다.
- `tracking/domain/BotUserAgents`: User-Agent를 소문자로 바꿔 패턴(부분 문자열)과 비교한다. 기본 패턴은 `googlebot, bingbot, yandexbot, baiduspider, duckduckbot, applebot, yeti, daumoa, facebookexternalhit, facebot, twitterbot, slackbot, slack-imgproxy, linkedinbot, discordbot, telegrambot, whatsapp, kakaotalk-scrap, headless, curl/, wget/, python-requests, python-urllib, go-http-client, libwww-perl, httpie, java-http-client, okhttp, apache-httpclient, java/`. `app.tracking.bot-user-agents`(목록)로 통째로 바꿀 수 있다(합치지 않는다). User-Agent가 없거나 비어 있으면 봇이다(브라우저는 항상 보낸다).
  - `bot` 같은 흔한 단어는 넣지 않는다. 인앱 브라우저(KAKAOTALK, Instagram, FBAN 등)와 휴대폰 모델명에 걸리는 오탐이 미탐보다 나쁘다.
  - HTTP 라이브러리 UA(`java-http-client`, `okhttp`, `apache-httpclient`, `java/` 등)는 사람이 폼을 여는 경로가 아니므로 봇이다. 목록을 테스트 도구에 맞추지 않는다: 통합 테스트는 공개 출처 요청에 브라우저 UA를 기본으로 싣는다(`support/PublicOrigin`).
- `VisitRecorder`가 기록할 때 판정해 `Visit.record(..., bot, ...)`로 넘긴다. 봇 방문도 행은 남긴다(나중에 패턴을 고쳐 다시 볼 수 있게).
- **방문자 쿠키**: 봇 UA에도 `lm_vid`를 똑같이 발급한다. 응답을 UA로 바꾸지 않으므로 크롤러 미리보기(제목 등)가 사람과 같고 코드 경로가 하나다. 크롤러는 보통 쿠키를 다시 보내지 않아 방문자 수에 영향이 없고, 어차피 지표에서 빠진다.
- **지표**: `StatsQuery`의 모든 방문 CTE(`visits_in`: 캠페인·캠페인 목록, 채널, 크리에이터·크리에이터 × 채널이 함께 쓰는 `CREATOR_SCOPE`, 크리에이터의 캠페인별)에 `AND NOT v.is_bot`을 넣는다. 신청에는 봇 표시가 없어 그대로 센다. 그래서 봇 UA로 신청까지 한 방문자는 신청 수에는 들어가지만 전환 방문자에는 들어가지 않는다(ADR 0005 변경 기록).

### Honeypot

- 이름은 `_lm_trap`이다. 템플릿 필드 이름 규칙(영문자로 시작, ADR 0004)이 밑줄로 시작하는 이름을 허용하지 않으므로 운영자 필드와 절대 겹치지 않는다. 그래서 따로 예약하거나 업로드를 거부할 필요가 없다. (처음 안은 `hp`였으나 "핸드폰" 약어로 실제 필드와 겹치고 자동 완성을 부를 수 있어, 이미 게시된 폼의 실제 신청을 조용히 버릴 위험 때문에 바꿨다.)
- 래퍼 브리지가 sandbox iframe 안의 업로드 폼에 `<input name="_lm_trap">`을 붙인다. 속성은 `autocomplete="off"`, `tabindex="-1"`, `aria-hidden="true"`이고, 위치·크기·투명도(`position:absolute; left:-10000px; width:1px; height:1px; overflow:hidden; opacity:0; pointer-events:none`)는 `style.setProperty(..., 'important')`로 인라인 `!important`를 준다. CSS 캐스케이드에서 인라인 `!important`는 스타일시트의 `!important` 규칙보다 우선하므로 템플릿 CSS만으로는 보이게 만들 수 없다. 스크립트(`style.removeProperty`, `removeAttribute('style')` 등)만 바꿀 수 있다.
- 한계: 업로드 HTML의 스크립트와 CSS는 같은 문서라 이 입력을 볼 수 있다(스크립트로 지우거나, 인라인 스타일을 걷어 드러내거나, 값을 채울 수 있다). 업로드 HTML은 운영자가 올린 것이라 공격자 모델 밖이고, 템플릿이 실수로 드러내더라도 사람에게 빈 칸 하나가 보일 뿐 채우지 않으면 정상 신청이다. 폼의 모든 입력을 채우는 봇을 거르는 것이 목적이다.
- 래퍼는 브리지가 보낸 `fields`에서 `_lm_trap`을 꺼내 제출 `fields._lm_trap`에 항상 싣는다(사람이면 `""`).
- 서버(`LeadSubmissionService`)는 `fields._lm_trap`에 빈 문자열이 아닌 값이 있으면 **201 `{status:"received"}`를 그대로 주되 저장하지 않는다**. 키가 없거나 값이 `""`이면 사람이다(래퍼를 거치지 않은 직접 호출은 토큰이 막는다). 봇에게 차단 사실을 알리지 않는다. 로그에는 `honeypot 제출 무시(저장 안 함): formId=…, 누적=N`만 INFO로 남기고 입력값은 남기지 않는다(봇이 보낸 값도 개인정보일 수 있다).
- `SubmissionValidator`는 `_lm_trap`을 검증·저장 대상에서 뺀다(`unknown_field`가 아니고 payload에도 넣지 않는다, 필드 개수에도 세지 않는다). `hp`는 보통 필드 이름이다.

### 작성 시간 토큰 `ft`

- `GET /api/public/forms/{slug}` 응답에 `ft`를 더한다. 모양은 `base64url(slug|발급시각epochMs|base64url(HMAC-SHA256(key, "slug|발급시각epochMs")))`(패딩 없음).
- 키는 새 환경 변수 `FORM_TOKEN_KEY`(base64 32바이트, `app.form-token.key`). 없거나 형식이 틀리면 기동이 실패한다(`form/config/FormTokenProperties`). `LEAD_ENC_KEY`·`LEAD_HMAC_KEY`와 같아도 기동이 실패한다(`FormTokenConfig`). 개인정보 키(`LEAD_*`)와 나눈 이유: 이 키는 공개 API로 나가는 토큰의 서명 키라 노출 범위와 교체 부담이 다르다(바꾸면 열려 있던 페이지의 토큰만 무효가 된다).
- 래퍼는 받은 `ft`를 제출 본문 최상위 `ft`로 그대로 보낸다.
- 서버는 다음이면 **422 `invalid_form_token`**("잠시 후 다시 제출해 주세요")으로 거절한다. 사유는 응답에 싣지 않고 DEBUG 로그에만 남긴다(토큰 값은 남기지 않는다).
  - 토큰 없음, 256자 초과, base64url·구분자·숫자 형식 오류
  - 서명 불일치(상수 시간 비교). 서명을 먼저 확인하고, 서명이 맞을 때만 발급 시각을 해석한다
  - 경로의 slug와 토큰의 slug가 다름
  - 발급 후 **2초 미만**(발급 시각이 미래인 경우 포함): 사람이 폼을 채웠다고 보기 어렵다
  - 발급 후 **24시간 초과**: 오래 열어 둔 탭이거나 재사용
- 2초·24시간은 설정으로 빼지 않는다(`FormToken.MIN_AGE`·`MAX_AGE`). 테스트는 주입한 `Clock`(`MutableClock`)을 옮기고, E2E는 제출 전에 2.1초 기다린다.
- 토큰은 상태가 없다. 24시간 안에서는 여러 번 쓸 수 있다(반복은 rate limit이 막는다). 한 번만 쓰게 하려면 저장소가 필요해 이번에는 하지 않는다.
- 래퍼는 422 `invalid_form_token`을 받으면 안내 문구를 보여 주고 폼을 다시 조회해 토큰만 바꿔 둔다. 사람이 문구를 읽고 다시 제출하면(보통 2초 이상 지난다) 통과한다.

### 제출 rate limit

- `POST /api/public/forms/{slug}/submissions`에만 건다. 인메모리 토큰 버킷(`common/web/RateLimiter`): 버킷 크기 = 분당 한도, 60초에 한도만큼 고르게 채운다.
  - 원격 주소(`getRemoteAddr()`)당 분당 10회: `app.public.rate-limit.ip-per-minute`
  - 방문자 쿠키(`lm_vid`, UUID v4일 때만)당 분당 5회: `app.public.rate-limit.visitor-per-minute`. 쿠키가 없거나 형식이 틀리면 IP 한도만 본다.
  - IP 한도를 먼저 보고, 통과하면 방문자 한도를 본다(방문자 한도에 걸린 요청도 IP 토큰 하나를 쓴다).
- 넘으면 **429 `too_many_requests`** ProblemDetail과 `Retry-After`(다음 토큰까지의 초, 1 이상). 공개 응답이라 CSP·nosniff 등 보안 헤더도 붙는다. `ErrorCode.forStatus(429)`도 이 코드로 묶어 Spring 표준 예외의 429도 같은 type·code가 된다.
- **메모리 상한**: 한도별로 키를 최대 `app.public.rate-limit.max-keys`(기본 10,000)개만 기억한다. 접근 순서 `LinkedHashMap`(LRU)으로 가장 오래 안 쓴 키부터 잊는다. 새 의존성(Caffeine 등)은 넣지 않았다(Spring Boot가 버전을 관리하지만 클래스패스에 없고, 필요한 기능이 LRU 하나다). 잊힌 키는 가득 찬 버킷으로 다시 시작하므로, 키를 상한보다 많이 바꿔 가며 보내는 공격은 이 제한만으로 막지 못한다(메모리를 지키는 쪽을 택했다).
- **X-Forwarded-For를 읽지 않는다**: 클라이언트가 마음대로 넣을 수 있고 신뢰할 프록시 설정이 없다(ADR 0016과 같은 이유). 그래서 리버스 프록시 뒤에 배포하면 모든 요청이 프록시 주소 하나로 보여 **IP 한도가 사실상 서비스 전체 한도**가 된다. 배포할 때 `server.forward-headers-strategy: native`(또는 `framework`)와 Tomcat `RemoteIpValve`의 신뢰 프록시(`server.tomcat.remoteip.internal-proxies`)를 함께 정해 `getRemoteAddr()`가 실제 클라이언트 주소가 되게 한다. 이 코드는 그때도 바꿀 필요가 없다.
- **서버 인스턴스마다 따로 센다**. 여러 대로 늘리면 한도가 대수만큼 커진다(공유 저장소 기반 제한은 ROADMAP).
- **거절 기록(ADR 0016 결과 절)**: 거절을 요청마다 기록하면 공격이 그대로 쓰기 폭주가 된다. 그래서 감사 로그에는 쓰지 않고(신청 제출은 감사 대상이 아니다), 애플리케이션 로그에도 마지막 기록에서 1분이 지났을 때 한 줄로 그동안의 거절 건수만 남긴다(`신청 제출 rate limit 거절: 지난 기록 이후 N건`, IP·쿠키 값 없음). 모인 건수는 거절이든 통과든 다음 제출 때 1분이 지났으면 기록하므로, 공격이 멈춘 뒤의 남은 건수도 사라지지 않는다(스케줄러는 두지 않았다. 제출이 전혀 없으면 다음 제출까지 기록이 늦어진다). 로그인 시도 제한은 이번 범위가 아니다(아래 결과).

### 검사 순서

제출 하나는 다음 순서로 거른다. 앞 단계에서 걸리면 뒤는 보지 않는다.

1. 403 `forbidden_origin` (`Origin: null`) — `SubmissionGuardFilter`
2. 413 `payload_too_large` — 같은 필터
3. 429 `too_many_requests` — 같은 필터. 403·413으로 끝난 요청은 토큰을 쓰지 않는다
4. 400 `malformed_request`·415 `unsupported_media_type` — MVC 본문 변환
5. 404 `not_found`·410 `gone` — 폼 상태(`PublicFormService.getOpen`). 공개 중이 아니면 토큰과 무관하게 이 응답이다
6. 422 `invalid_form_token` — `FormTokenService.verify`
7. honeypot → 201(저장 안 함)
8. 422 `validation_failed` — `SubmissionValidator`
9. 409 `duplicate_submission` — DB

honeypot을 필드 검증보다 앞에 둔 이유: 봇이 엉터리 값을 보내도 422로 알려 주지 않고 같은 201을 받게 하려는 것이다. 토큰을 honeypot보다 앞에 둔 이유: 토큰이 없는 스크립트는 422로 끝나 honeypot 로그를 채우지 않는다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| 봇 방문은 기록하지 않기 | 테이블이 작다 | 패턴을 고친 뒤 다시 볼 수 없고, 오탐이면 방문이 사라진다 | 표시만 하고 지표에서 빼는 쪽이 되돌릴 수 있다 |
| 봇 UA에는 방문자 쿠키를 주지 않기 | 쿠키 발급이 줄어든다 | 응답이 UA에 따라 달라지고 코드 경로가 둘이 된다. 오탐이면 그 사람의 전환이 끊긴다 | 지표에서 빼는 것으로 충분하다 |
| 봇 UA의 신청도 거절 | 스팸이 줄어든다 | 인앱 브라우저 UA 오탐 시 실제 신청을 잃는다 | 스팸은 토큰·honeypot·rate limit이 막는다 |
| CAPTCHA(reCAPTCHA 등) | 강한 차단 | 외부 스크립트가 CSP(ADR 0002)를 깨고 방문자 데이터가 제3자에게 간다. 전환율이 떨어진다 | 이번 위협 수준에 과하다 |
| honeypot을 래퍼 페이지(iframe 밖)에 두기 | 브리지 수정 없음 | 봇은 업로드 폼(iframe 안)을 채우므로 밖의 입력은 채우지 않는다 | 봇이 보는 폼 안에 있어야 의미가 있다 |
| honeypot이 채워지면 422 | 구현이 단순 | 봇이 차단을 알아채고 우회한다 | 정상과 같은 201이 낫다 |
| 작성 시간을 클라이언트가 재서 보내기 | 서버 상태·키 불필요 | 값을 마음대로 보낼 수 있다 | 서버 서명이 있어야 의미가 있다 |
| 토큰을 서버에 저장해 한 번만 쓰기 | 재사용 차단 | 저장소·정리 작업이 필요하다 | rate limit으로 충분하다. 필요하면 다시 본다 |
| 최소 작성 시간을 설정으로 | E2E·테스트가 쉽다 | 운영에서 0으로 두는 실수가 생긴다 | 테스트는 Clock을, E2E는 대기를 쓴다 |
| 래퍼가 2초가 안 됐으면 알아서 기다렸다 보내기 | 사람이 422를 볼 일이 없다 | 브라우저를 쓰는 봇도 자동으로 통과한다 | 서버 거절 + 안내 + 토큰 재발급으로 사람의 재시도만 돕는다 |
| Caffeine 캐시로 버킷 저장 | 만료·크기 제한이 검증된 구현 | 새 의존성 | LRU 하나면 충분하다 |
| Bucket4j 등 rate limit 라이브러리 | 기능이 많다 | 새 의존성, 이 규모에 과하다 | 토큰 버킷은 60줄이면 된다 |
| `X-Forwarded-For` 첫 값으로 IP | 프록시 뒤에서도 실제 주소 | 신뢰 프록시 설정 없이는 위조 가능(헤더만 바꾸면 한도 우회) | 배포 시 `forward-headers-strategy`와 신뢰 프록시를 정하면서 바꾼다 |
| 거절마다 감사 로그 한 행 | 추적이 쉽다 | 공격이 감사 테이블 쓰기 폭주가 된다(ADR 0016 결과 절) | 1분 단위 건수 한 줄로 충분하다 |

## 결과

- 쉬워지는 것: 링크 미리보기 크롤러가 방문·방문자를 부풀리지 않는다. 직접 API를 부르는 단순 스크립트는 토큰이 없어 422, 폼을 채우는 봇은 honeypot으로 조용히 버려지고, 반복 제출은 429로 막힌다.
- 어려워지는 것: 공개 폼 조회마다 HMAC 한 번, 제출마다 버킷 조회가 늘어난다(무시할 수준). 새 시크릿 `FORM_TOKEN_KEY`가 생겼다. E2E처럼 빠른 자동 입력은 2초를 기다려야 한다.
- 알려진 한계:
  - UA를 사람처럼 바꾼 봇 방문은 지표에 들어간다. headless 브라우저도 UA를 바꾸면 통과한다.
  - 토큰은 폼을 한 번 조회하면 24시간 쓸 수 있다. 조회 → 2초 대기 → 제출을 자동화하는 봇은 rate limit으로만 늦춘다.
  - 같은 IP 뒤의 여러 사람(회사·학교 NAT, 통신사 CGNAT)은 IP 한도(분당 10회)를 나눠 쓴다. 신청은 사람당 한두 번이라 보통은 문제가 없다.
  - 서버 재시작 시 버킷이 비워진다(한도가 처음부터 다시 시작한다).
- 로그인 시도 제한(ADR 0016 결과 절이 #18로 미룬 것)은 이번 범위(공개 신청 제출)에서 뺐다. ROADMAP에 남기며, 넣을 때는 이 ADR의 `RateLimiter`를 쓰고 ADR 0016의 기록 방식(제한이 걸린 시점에 한 번 + 거절 건수 요약)을 따른다.
- 다시 볼 조건: 리버스 프록시 뒤 배포(신뢰 프록시와 `getRemoteAddr()`), 서버 여러 대(공유 저장소 기반 제한), 봇 스팸이 늘면 토큰 일회용화나 CAPTCHA.
