# 0016. 감사 로그와 명단 CSV 다운로드

- 상태: 승인
- 날짜: 2026-10-24

## 맥락

ADR 0015로 명단은 가린 값만 보이고 원문은 `GET /api/admin/leads/{id}/contact`로만 열린다. 그러나 관리자 계정이 탈취되거나 운영자가 필요 이상으로 원문을 보는 것은 막지 못한다(0015의 범위 밖). 개인정보를 다루는 CRM이므로 "누가, 언제, 어디서, 어떤 신청자 정보를 봤는지"를 사후에 추적할 수 있어야 한다(개인정보 접근 기록). 이번에 명단을 파일로 내보내는 CSV 다운로드도 더하는데, 한 번에 많은 연락처가 밖으로 나가므로 이 역시 기록 대상이다.

모호한 점과 가정:

- **기록 실패 시 요청을 막을지**: 기록이 원문 노출보다 중요하다고 본다. 단 단순 조회까지 막으면 감사 저장소 장애가 명단 화면 전체 장애가 된다.
- **IP**: 배포 환경의 리버스 프록시 구성이 정해지지 않았고(ROADMAP), 신뢰할 프록시 목록 설정도 없다.
- **CSV의 추가 항목**: payload 키는 폼마다 다르다. 열로 펼치려면 내보낼 행을 두 번 읽어야 한다.

## 결정

- **테이블**(`V6__audit_logs.sql`): `audit_logs(id, operator_id, action VARCHAR(40), target_type VARCHAR(40), target_id, campaign_id, detail JSONB, ip VARCHAR(45), user_agent VARCHAR(512), created_at)`, 인덱스 `(created_at)`, `(operator_id, created_at)`, `(campaign_id, created_at)`. 기록이 대상(운영자·캠페인)보다 오래 남도록 외래 키를 두지 않는다. 애플리케이션은 쓰기와 조회만 하고 수정·삭제 API는 없다.
- **기록 대상**(`audit/domain/AuditAction`):

  | action | 언제 | target | detail |
  |---|---|---|---|
  | `lead.list` | 명단 조회 성공 | campaign | `formId`, `channel`, `page`, `size` |
  | `lead.contact.view` | 원문 열람 성공 | lead(신청 id), campaign_id | 없음 |
  | `lead.export` | 가린 값 CSV 다운로드 | campaign | `formId`, `channel`, `reveal: false`, `count` |
  | `lead.export.reveal` | 원문 CSV 다운로드 | campaign | `formId`, `channel`, `reveal: true`, `count` |
  | `auth.login.success` | 로그인 성공 | operator | 없음 |
  | `auth.login.failure` | 로그인 실패 | operator(id 없음) | `email`(가린 값, `o***@example.com`) |

  검증 실패(422)·없는 대상(404)처럼 아무것도 보여주지 않은 요청은 남기지 않는다. 감사 로그 조회 자체도 남기지 않는다(신청자 개인정보가 없다).
- **detail에 원문 개인정보 금지**: 필터·페이지·건수·가린 이메일만 넣는다. 통합 테스트가 DB의 `detail`과 애플리케이션 로그(OutputCapture)에 이름·전화번호·이메일 원문이 없는지 확인한다.
- **기록 실패 정책**(`audit/application/AuditService`):
  - 원문 열람·CSV 다운로드(가린 값 포함): `recordRequired`. 유스케이스의 쓰기 트랜잭션 안에서 `saveAndFlush`하고, 실패하면 예외가 올라가 500(`internal_error`)이며 원문·파일을 주지 않는다. 가린 값 CSV도 대량 반출이라 같은 정책을 쓴다.
  - 명단 조회·로그인: `recordBestEffort`. 유스케이스 트랜잭션이 커밋·롤백된 **뒤** 컨트롤러(`LeadController`, `AuthController`)가 부르고, 자기 트랜잭션으로 쓴다. 실패하면 `감사 로그 기록 실패(요청은 계속 처리): action=…, 원인=예외 클래스`만 WARN으로 남긴다(DB 오류 메시지에 행 값이 실릴 수 있어 메시지는 남기지 않는다). 트랜잭션 안에서 `REQUIRES_NEW`로 쓰면 바깥 트랜잭션이 커넥션을 쥔 채 두 번째 커넥션을 잡아, 몰리는 로그인·명단 요청이 커넥션 풀을 두 배로 쓰고 최악에는 풀 고갈로 서로 기다린다. 그래서 트랜잭션 안에서 부르면 `IllegalStateException`으로 막는다. `TransactionSynchronization.afterCompletion`은 Spring이 바깥 커넥션을 반납하기 전에 불려 같은 문제가 남으므로 쓰지 않았다. 로그인 실패 기록은 로그인 트랜잭션 롤백과 상관없이 남는다.
  - 두 경로 모두 테스트에서 `@MockitoSpyBean AuditLogRepository`로 쓰기를 실패시켜 확인한다.
- **CSV 다운로드와 "같은 트랜잭션"**: 응답을 스트리밍하므로 기록과 파일 쓰기를 한 트랜잭션에 묶으면 기록 실패를 알 때 이미 200을 보냈을 수 있다. 그래서 두 단계로 나눈다. (1) `LeadExportService.start`가 필터를 검증하고 내보낼 범위(지금 보이는 가장 큰 신청 id = maxId)를 정한 뒤, 스트림과 같은 조건(`id <= maxId`)으로 건수를 세어 같은 트랜잭션에서 기록하고 커밋한다. 실패하면 첫 바이트 전에 500이다. (2) 같은 조건의 행만 id 순으로 500건씩 keyset(`id > 직전 id`, `LIMIT 500`, 배치마다 COUNT 없음)으로 읽어 쓴다. 신청은 바뀌거나 지워지지 않으므로 기록한 건수와 파일의 행 수는 같다. 단 동시에 들어온 신청이 id 순서와 다르게 커밋되면(maxId보다 작은 id가 기록 뒤에 커밋) 파일에 그 행이 더 들어갈 수 있다. 신청 id는 시퀀스라 이 차이는 다운로드 순간 진행 중이던 신청 몇 건에 그친다. 스트리밍 중에 연결이 끊기면 "다운로드 시도"가 기록으로 남는다(덜 기록하는 것보다 더 기록하는 쪽을 택했다). 스트리밍은 MVC 비동기 요청이라 제한 시간을 `spring.mvc.async.request-timeout: 10m`으로 명시했다(기본값인 Tomcat 30초면 큰 명단이 중간에 끊긴다). 다른 API는 비동기를 쓰지 않아 영향이 없다.
- **CSV 형식**: `GET /api/admin/campaigns/{id}/leads.csv?formId&channel&reveal`. UTF-8 BOM(엑셀 한글), RFC 4180(쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싸고 따옴표는 두 번, 줄 끝 CRLF). 열은 신청일(Asia/Seoul `yyyy-MM-dd HH:mm:ss`), 이름, 전화번호, 이메일, 채널, 크리에이터, 폼, 추가 항목(payload를 JSON 한 칸으로). 신청 순(id 오름차순). 기본은 가린 값, `reveal=true`면 원문(전화번호는 `010-1234-5678`처럼 하이픈을 넣는다. 숫자만 쓰면 엑셀이 숫자로 읽어 앞의 0을 지운다. 규칙은 관리자 화면 `formatPhone`과 같고 `common/PhoneFormat`이 한다). 복호화하지 못한 행은 연락처 세 칸에 "복호화 불가"(로그에는 신청 id만). 응답 헤더는 `Content-Type: text/csv;charset=UTF-8`, `Cache-Control: no-store`, `Content-Disposition: attachment; filename="leads-campaign-{id}-{yyyyMMdd-HHmmss}[-raw].csv"`(ASCII만).
- **CSV 수식 주입 방지**: 앞의 공백 문자(스페이스, 탭, CR, LF)를 건너뛴 첫 글자가 `=`, `+`, `-`, `@`이거나 값이 탭·CR로 시작하면 앞에 `'`를 붙인다(`common/Csv`). 스프레드시트가 앞 공백을 무시하고 수식으로 읽는 경우도 막는다. `'`는 CSV를 열면 셀에 그대로 보인다(데이터로 들어가기 때문이다. 지우면 수식이 다시 살아난다). 신청자가 이름에 `=HYPERLINK(...)`를 넣어도 스프레드시트가 수식으로 실행하지 않는다. 가린 전화번호(`010-****-5678`)처럼 숫자로 시작하는 값은 그대로 둔다.
- **요청 주체**: `audit/api/AuditActors`가 요청에서 `audit/domain/AuditActor`(운영자 = JWT `sub`, IP, User-Agent)를 만든다. auth·lead·campaign 등이 audit을 한 방향으로 쓴다(#55에서 common/web에서 옮겼다).
- **패키지 방향**: auth·lead → audit 한 방향이다. 감사 로그 화면의 운영자 이메일은 audit이 정의한 읽기 포트 `OperatorDirectory`를 auth가 구현해 api 계층(`AuditLogController`)이 붙인다. `AuditService.search`는 운영자 id만 돌려준다.
- **IP·User-Agent**: IP는 `request.getRemoteAddr()`만 쓴다. `X-Forwarded-For`는 클라이언트가 마음대로 넣을 수 있고 신뢰할 프록시 설정이 없어 읽지 않는다. 그래서 리버스 프록시 뒤에 배포하면 프록시 주소가 남는다. User-Agent는 512자, IP는 45자로 자른다(`AuditActor`).
- **조회 API**: `GET /api/admin/audit-logs?action&operatorId&campaignId&from&to&page&size`. 최신순(created_at, id 내림차순), size 1~100(기본 20), 모르는 action·잘못된 기간은 422. 기간은 지표와 같은 `DateRange`(Asia/Seoul 날짜, 양 끝 포함). 응답에 운영자 이메일을 붙인다(운영자 계정 정보이지 신청자 개인정보가 아니다).
- **관리자 화면**: 명단 탭의 "CSV 다운로드" 메뉴(마스킹/원문). 원문은 감사 로그에 기록된다고 알리는 확인 대화상자를 거친다. access token이 메모리에만 있어 `<a href>`로는 인증을 실을 수 없으므로 API 클라이언트(`apiDownload`, 401 → refresh 재시도 포함)로 받아 Blob → object URL로 저장한다. 사이드바 "감사 로그" 화면은 행동·캠페인·기간 필터와 페이지를 URL 쿼리에 둔다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| 모든 기록 실패를 요청 실패로 | 기록 누락이 없다 | 감사 저장소 장애가 명단 화면·로그인 장애가 된다 | 원문을 내주는 경로만 막으면 위험은 충분히 줄어든다 |
| 모든 기록을 비동기(이벤트·큐)로 | 응답이 빠르고 장애가 번지지 않는다 | 원문을 준 뒤 기록이 사라질 수 있다 | 원문 열람은 기록이 먼저여야 한다 |
| 애플리케이션 로그(파일)에만 남기기 | 테이블이 필요 없다 | 조회·필터가 어렵고 로그 보존 정책에 묶인다 | 화면에서 찾아볼 수 있어야 한다 |
| `X-Forwarded-For` 첫 값을 IP로 | 프록시 뒤에서도 실제 주소 | 신뢰할 프록시 설정 없이는 위조 가능 | 위조된 IP가 기록되는 편이 더 나쁘다. 배포 시 `server.forward-headers-strategy`와 신뢰 프록시를 정하면서 바꾼다 |
| CSV를 메모리에 다 만든 뒤 응답 | 기록과 파일을 한 트랜잭션에 묶기 쉽다 | 명단이 커지면 메모리를 많이 쓴다 | 기록 → 범위 고정 → 스트리밍으로 같은 보장을 얻는다 |
| payload 키마다 열로 펼치기 | 엑셀에서 보기 좋다 | 폼마다 키가 달라 행을 두 번 읽어야 하고 열 이름이 흔들린다 | JSON 한 칸으로 두고, 필요하면 폼별 내보내기에서 다시 본다 |
| 원문 CSV 링크(`<a href>`) + 쿠키 인증 | 브라우저 기본 다운로드 | access token을 쿠키·URL에 실어야 한다 | 토큰은 메모리에만 둔다는 불변식(ADR 0011)을 깬다 |

## 결과

- 쉬워지는 것: 원문 열람·다운로드는 기록 없이 일어날 수 없다. 누가 언제 어느 캠페인의 명단을 보고 내려받았는지 화면에서 찾을 수 있다. 엑셀로 명단을 받을 수 있다.
- 어려워지는 것: 원문 열람과 다운로드가 감사 저장소(같은 PostgreSQL)에 의존한다. 명단 조회마다 쓰기가 한 번 늘어난다. 로그인 실패 기록이 쌓이므로 무차별 대입 시 테이블이 커진다.
- rate limit(이슈 #18, 로그인 시도 제한 포함)과의 관계: 제한에 걸려 거절된 시도를 요청마다 `auth.login.failure`로 남기면 공격이 그대로 감사 테이블 쓰기 폭주가 된다. #18에서는 거절된 시도를 한 건씩 남기지 말고, 제한이 걸린 시점에 한 번(예: `auth.login.throttled`, detail에 가린 이메일·IP·창 길이·거절 건수)을 남기고 창이 끝날 때 거절 건수를 합쳐 갱신하거나 한 줄로 요약한다. 제한에 걸리기 전의 실패는 지금처럼 한 건씩 남긴다.
- 내려받은 파일은 시스템 밖으로 나가므로 그 뒤의 보관·파기는 추적하지 못한다. 확인 대화상자에서 운영자에게 알린다.
- 다시 볼 조건(후속 작업): 리버스 프록시 뒤 배포 시 신뢰 프록시와 IP 기록 방식, 감사 로그 보존기한과 파기(이슈 #19), 감사 로그 위변조 방지(해시 체인·별도 저장소), 비정상 다운로드 알림.

## 변경 기록

- 2026-10-24 (ADR 0019): 이슈 #18은 공개 신청 제출에만 rate limit을 걸었다. 거절은 감사 로그에 쓰지 않고 애플리케이션 로그에 1분 단위 건수 한 줄만 남긴다(위 "결과" 절의 원칙). 로그인 시도 제한은 이번에 넣지 않았고, 넣을 때 위 절의 `auth.login.throttled` 방식을 따른다.
- 2026-10-24 (ADR 0020): `retention.anonymize`(보존 기간 파기, 실행마다 한 줄, detail `trigger`·`count`·`completed`·`interrupted`·실패 시 `error`, 스케줄 실행은 운영자 없음)와 `campaign.retention.update`(캠페인 보존 기간 변경, detail `oldRetentionDays`·`newRetentionDays`, 같은 트랜잭션 `recordRequired`)를 더했다. campaign → audit 의존이 생겼다. 원문 API의 410 `lead_anonymized`와 동의 문구 조회(`/api/admin/leads/{id}/consent`, 개인정보 아님)는 기록하지 않는다. CSV에 마지막 열 "파기"가 생겼다. 감사 로그 자체의 보존 기간은 여전히 후속 작업이다.
- 2026-10-24 (ADR 0021): IP 출처가 바뀌었다. 감사 IP는 여전히 `getRemoteAddr()`이지만, admin 포트에서 직전 피어가 신뢰 프록시(`TRUSTED_PROXIES`, 기본 compose의 admin-web 고정 주소 `172.16.57.10/32`)면 `common/web/ClientIpFilter`가 `X-Forwarded-For`를 오른쪽부터 읽어 고른 클라이언트 주소다. public 포트와 신뢰하지 않는 피어는 피어 주소 그대로다. 위 "IP·User-Agent"와 대안 표의 "`X-Forwarded-For` 첫 값" 행은 이 규칙으로 대체된다(첫 값이 아니라 신뢰 프록시를 건너뛴 오른쪽 값이다). 로그인 시도 제한도 결과 절의 방식대로 넣었다: 잠금 시작에 `auth.login.locked` 한 줄(detail `email` 가린 값(IP 한도만 닿았으면 없음)·`scopes`(`email_ip`·`email`·`ip`)·`windowMinutes`), 잠긴 뒤의 거절은 감사에 쓰지 않고 애플리케이션 로그에 1분 단위 건수만 남긴다. 결과 절에서 예로 든 `auth.login.throttled`라는 이름과 "창이 끝날 때 갱신"은 쓰지 않았다(감사 행을 고치지 않는다).
- 2026-10-24 (이슈 #53): 감사 공백 여섯 가지를 더했다. action은 `VARCHAR(40)`에 모두 들어간다(가장 긴 `template.version.upload`는 23자, 마이그레이션 없음). 새 action 모두 API 필터 검증(`AuditAction`)과 관리자 감사 화면의 행동 필터·요약 문구에 들어간다.

  | action | 정책 | target | detail |
  |---|---|---|---|
  | `auth.refresh.reused` | 최선 노력 | operator | 없음 |
  | `auth.logout` | 최선 노력 | operator | 없음 |
  | `form.publish`, `form.close` | 필수(같은 트랜잭션) | form, campaign_id | `formId`, `slug` |
  | `template.version.upload` | 필수 | template | `templateId`, `version`(올린 뒤), `affectedDraftForms` |
  | `creator.archive` | 필수 | creator | `creatorId` |

  - `auth.refresh.reused`: 재사용을 감지해 패밀리를 폐기한 뒤 남긴다. 폐기는 `noRollbackFor`로 이미 커밋되었으므로 `AuthController.refresh`가 `RefreshTokenReusedException`(운영자 id 보유, `DomainException` 하위)을 잡아 트랜잭션 밖에서 `recordBestEffort`하고 같은 예외를 다시 던진다. 기록이 실패해도 응답은 401 `refresh_token_reused`이고 폐기는 유지된다. 모르는 토큰(`invalid_refresh_token`)은 운영자를 알 수 없어 남기지 않는다. 이미 폐기된 패밀리의 토큰을 다시 보내는 반복 재생은 폐기한 토큰이 0개이므로 남기지 않는다(`/refresh`에 시도 제한이 없어 재생마다 행이 쌓이는 것을 막는다).
  - `auth.logout`: `AuthService.logout`이 쿠키가 운영자로 확인되고 살아 있는 토큰을 실제로 폐기했을 때만 그 id를 돌려주고(이미 로그아웃·폐기된 쿠키는 기록하지 않는다), 컨트롤러가 최선 노력으로 남긴다. 쿠키가 없거나 모르는 토큰이면 아무것도 쓰지 않고 응답은 그대로 204다.
  - 폼 게시·마감, 템플릿 새 버전, 크리에이터 보관은 공개 콘텐츠나 공개 폼에 영향을 주는 상태 변경이라 같은 트랜잭션에서 `recordRequired`한다. 기록이 실패하면 변경도 롤백되고 500 `internal_error`다. 실제로 전이가 일어난 경우만 남기고(검증·409 실패, 내용만 수정은 제외) 잠금 순서(템플릿 → 폼)는 바꾸지 않는다.
  - detail에는 id, slug(공개 주소의 일부), 버전, 건수만 넣는다. 크리에이터 이름·핸들, 토큰 값, 이메일은 넣지 않는다. 서비스 시그니처에 `AuditActor`가 추가되어 form·creator → audit 의존이 생겼다(auth·lead·campaign과 같은 방향).
- 2026-10-24 (#55): `AuditActor`·`AuditActors`를 `common/web`에서 audit 패키지(`audit/domain`, `audit/api`)로 옮겼다. 동작은 같고, common이 도메인을 모르게 하려는 패키지 정리다. 감사 기록 목록 조회의 기간은 `common/DateRange`를 쓴다.
- 2026-10-24 (#100): `stats.export`(대시보드 폼 시트 CSV, `GET /api/admin/stats/dashboard.csv`)를 더했다. 집계 값만 있고 개인정보가 없어 트랜잭션이 끝난 뒤 컨트롤러가 `recordBestEffort`로 남긴다(실패해도 파일은 내려간다). target `dashboard`(id 없음), detail `from`·`to`(적용된 기간)·`count`(폼 행 수). 대시보드 조회 자체는 가린 이름만 보여 주므로 기록하지 않는다.
- 2026-10-24 (ADR 0022): `pii.reencrypt`(개인정보 재암호화, 수동 실행마다 한 줄, `recordRequired`를 자기 트랜잭션으로, target `lead`, detail `count`·`unreadable`·`remaining`·`completed`·`interrupted`·실패 시 `error`)를 더했다. 건수만 남기고 신청 id·키 id·키 값은 넣지 않는다. 드라이런은 기록하지 않는다. HMAC 키 교체는 기동 중(요청 전, 운영자 없음)에 일어나 감사 로그가 아니라 애플리케이션 로그에 건수를 남긴다.
