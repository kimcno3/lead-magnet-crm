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
  - 명단 조회·로그인: `recordBestEffort`. 별도 트랜잭션(`REQUIRES_NEW`)으로 쓰고 실패하면 `감사 로그 기록 실패(요청은 계속 처리): action=…, 원인=예외 클래스`만 WARN으로 남긴다(DB 오류 메시지에 행 값이 실릴 수 있어 메시지는 남기지 않는다). 로그인 실패 기록은 로그인 트랜잭션이 롤백돼도 남는다.
  - 두 경로 모두 테스트에서 `@MockitoSpyBean AuditLogRepository`로 쓰기를 실패시켜 확인한다.
- **CSV 다운로드와 "같은 트랜잭션"**: 응답을 스트리밍하므로 기록과 파일 쓰기를 한 트랜잭션에 묶으면 기록 실패를 알 때 이미 200을 보냈을 수 있다. 그래서 두 단계로 나눈다. (1) `LeadExportService.start`가 필터를 검증하고 내보낼 범위(지금 있는 가장 큰 신청 id)와 건수를 정한 뒤 같은 트랜잭션에서 기록하고 커밋한다. 실패하면 첫 바이트 전에 500이다. (2) 그 범위의 행만 id 순으로 500건씩 읽어 쓴다. 신청은 바뀌거나 지워지지 않으므로 기록한 건수와 파일의 행 수가 같다. 스트리밍 중에 연결이 끊기면 "다운로드 시도"가 기록으로 남는다(덜 기록하는 것보다 더 기록하는 쪽을 택했다).
- **CSV 형식**: `GET /api/admin/campaigns/{id}/leads.csv?formId&channel&reveal`. UTF-8 BOM(엑셀 한글), RFC 4180(쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싸고 따옴표는 두 번, 줄 끝 CRLF). 열은 신청일(Asia/Seoul `yyyy-MM-dd HH:mm:ss`), 이름, 전화번호, 이메일, 채널, 크리에이터, 폼, 추가 항목(payload를 JSON 한 칸으로). 신청 순(id 오름차순). 기본은 가린 값, `reveal=true`면 원문(전화번호는 숫자만). 복호화하지 못한 행은 연락처 세 칸에 "복호화 불가"(로그에는 신청 id만). 응답 헤더는 `Content-Type: text/csv;charset=UTF-8`, `Cache-Control: no-store`, `Content-Disposition: attachment; filename="leads-campaign-{id}-{yyyyMMdd-HHmmss}[-raw].csv"`(ASCII만).
- **CSV 수식 주입 방지**: `=`, `+`, `-`, `@`, 탭, CR로 시작하는 값 앞에 `'`를 붙인다(`common/Csv`). 신청자가 이름에 `=HYPERLINK(...)`를 넣어도 스프레드시트가 수식으로 실행하지 않는다. 가린 전화번호(`010-****-5678`)처럼 숫자로 시작하는 값은 그대로 둔다.
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
- 어려워지는 것: 원문 열람과 다운로드가 감사 저장소(같은 PostgreSQL)에 의존한다. 명단 조회마다 쓰기가 한 번 늘어난다. 로그인 실패 기록이 쌓이므로 무차별 대입 시 테이블이 커진다(로그인 시도 제한은 ROADMAP).
- 내려받은 파일은 시스템 밖으로 나가므로 그 뒤의 보관·파기는 추적하지 못한다. 확인 대화상자에서 운영자에게 알린다.
- 다시 볼 조건(후속 작업): 리버스 프록시 뒤 배포 시 신뢰 프록시와 IP 기록 방식, 감사 로그 보존기한과 파기(이슈 #19), 감사 로그 위변조 방지(해시 체인·별도 저장소), 비정상 다운로드 알림.
