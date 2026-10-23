# v0.2.0

v0.1.0 core(캠페인·폼·채널 링크·방문·신청·지표) 위에, 크리에이터 성과형 협업과 개인정보를 다루는 CRM에 직접 연결되는 7가지를 더했다. 이제 시스템은 신청이 어느 채널과 어느 크리에이터에서 왔는지 증명하고, 개인정보를 암호화해 보관하며 열람 기록을 남기고 보존 기간이 지나면 익명화한다.

## 기능

- **크리에이터별 성과** ([ADR 0014](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0014-creator-attribution.md)): 크리에이터 등록·수정·보관, 폼 × 채널 × 크리에이터 단위 배포 링크, 크리에이터별 방문·신청·전환 지표. 크리에이터 없는 링크와 직접 접근은 "직접·자체 링크"로 묶는다.
- **개인정보 암호화** ([ADR 0015](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0015-pii-encryption.md)): 이름·전화번호·이메일을 AES-256-GCM(컬럼 이름을 AAD로 묶음)으로 저장하고 중복 판정은 HMAC-SHA256 해시로 한다. 명단은 가린 값만 보이고 원문은 한 건씩 따로 조회한다. 기동 시 `pii_key_check`로 키가 기존 데이터와 같은지 확인한다. 평문 컬럼은 삭제됐다.
- **감사 로그와 명단 CSV** ([ADR 0016](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0016-audit-log.md)): 원문 조회와 CSV 다운로드는 감사 기록을 못 쓰면 실패한다. 명단 조회와 로그인은 최선 노력으로 기록한다. CSV는 마스킹/원문 두 가지이고 수식 시작 문자를 무력화한다. 원문 CSV의 전화번호는 `010-1234-5678` 형식이라 엑셀에서 앞의 0이 유지된다.
- **업로드 HTML 검사** ([ADR 0017](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0017-template-inspection.md)): 업로드를 막지 않고 외부 스크립트·이미지·폰트·전송 시도 같은 경고를 보여 준다. 운영자용 AI 프롬프트 가이드([AI_FORM_PROMPT](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/AI_FORM_PROMPT.md))를 함께 제공한다.
- **게시 스냅샷과 템플릿 새 버전** ([ADR 0018](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0018-publish-snapshot.md)): 게시 시점의 HTML을 폼에 복사해 템플릿이 바뀌어도 공개 폼이 흔들리지 않는다. 템플릿 새 버전 업로드를 지원한다.
- **데이터 품질 방어** ([ADR 0019](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0019-data-quality-defense.md)): 봇 User-Agent 방문은 지표에서 제외하고, 제출에는 작성 시간 토큰(`ft`), honeypot, IP·방문자 rate limit(429)을 적용한다.
- **보존 기간 파기와 동의 증빙** ([ADR 0020](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0020-retention-and-consent-evidence.md)): 캠페인별 보존 기간(기본 365일)이 지난 신청을 매일 03:00(KST) 익명화한다. 지표용 값은 남기고 연락처는 지운다. 신청 시점의 동의 문구와 해시를 증빙으로 남기고 파기 뒤에도 조회할 수 있다.

최종 리뷰 반영: JWT_SECRET이 개인정보 키·폼 토큰 키와 같으면 기동을 멈춘다. `visits.link_id`·`leads.link_id` 인덱스(V11)를 더했다.

## v0.1.0에서 올릴 때 (반드시 읽기)

V4(암호화 컬럼 추가)와 V5(평문 컬럼 삭제)가 한 번에 적용된다. **v0.1.0 DB에 신청이 이미 있으면 V5의 가드(`V5 중단: 암호문·해시가 채워지지 않은 leads 행이 있습니다`)가 기동을 멈춘다.** 신청을 암호화해 채우던 백필 러너(PR #42)가 이 버전에는 없기 때문이다. 평문을 지우면 연락처가 사라지므로 가드가 막는 것이 맞다. 길은 둘이다.

1. **새 DB로 시작** (데이터가 필요 없을 때): `docker compose down -v` 후 `docker compose up --build`. DB의 모든 데이터가 지워진다.
2. **데이터를 지키며 올리기**:
   1. 커밋 `8b318db`(PR #42, 암호화 expand)를 체크아웃한다.
   2. 아래 키를 `.env`에 넣고 `docker compose up --build`로 한 번 기동한다(이미지를 다시 빌드해야 그 커밋의 코드로 뜬다). 기동 후 백필 러너가 기존 신청의 암호문·해시를 채운다.
   3. [ADR 0015](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/0015-pii-encryption.md)의 게이트 쿼리가 `0`인지 확인한다.
      ```sql
      SELECT count(*) FROM leads
      WHERE (name IS NOT NULL AND name_enc IS NULL)
         OR (phone IS NOT NULL AND (phone_enc IS NULL OR phone_hash IS NULL))
         OR (email IS NOT NULL AND (email_enc IS NULL OR email_hash IS NULL));
      ```
   4. v0.2.0을 체크아웃해 **같은 `.env`(같은 키)**로 `docker compose up --build` 한다. 키가 바뀌면 `pii_key_check`가 기동을 멈춘다.

신청이 없는 DB라면 키만 맞추면 그대로 올라간다.

## 필수 키 4개

backend는 아래 네 값이 모두 있고 서로 달라야 기동한다. 없거나 형식이 틀리거나 같으면 멈추고, 오류 메시지에는 값을 싣지 않는다.

| 환경 변수 | 형식 |
|---|---|
| `JWT_SECRET` | UTF-8 32바이트 이상 (`openssl rand -base64 48`) |
| `LEAD_ENC_KEY` | base64 32바이트 (`openssl rand -base64 32`) |
| `LEAD_HMAC_KEY` | base64 32바이트 |
| `FORM_TOKEN_KEY` | base64 32바이트 |

README의 `sed` 명령이 `.env.example`을 복사하면서 네 값을 새로 만든다. 새 DB가 아닌 곳에서 `.env`를 다시 만들면 개인정보 키가 바뀌어 기동이 멈추므로 기존 키를 보관한다. 키를 잃으면 암호화된 연락처는 복구할 수 없다.

## 마이그레이션

V3 크리에이터, V4 암호화 컬럼(expand), V5 평문 삭제(contract), V6 감사 로그, V7 템플릿 검사 결과, V8 게시 스냅샷, V9 봇 플래그, V10 보존 기간·동의 증빙, V11 link_id 인덱스. 큰 테이블에서는 V11의 인덱스를 `CREATE INDEX CONCURRENTLY`로 미리 만들어 두면 된다(마이그레이션이 `IF NOT EXISTS`로 건너뛴다).

## 테스트

- backend `./gradlew build -Dopenapi.verify=true`: 79개 테스트 클래스, 677개 테스트 통과(실패 0, verify 모드에서 export 쓰기 테스트 1건은 건너뜀)
- admin-web `npm test`: 18개 파일, 155개 테스트 통과
- 브라우저 E2E(Playwright): 24단계 모두 통과, 새 볼륨에서 실행 ([docs/e2e](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/e2e/results.json))

## 알려진 한계

[WORKPLAN](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/WORKPLAN.md)의 "다음 작업 (v0.3.0 후보)"에 있다. 주요한 것: 로그인 시도 제한 없음, 리버스 프록시 뒤 IP 기록·rate limit(`getRemoteAddr()`만 사용), `payload`에 들어온 평문 연락처 모양 값, PII 키 교체 절차 없음.

자세한 설계는 [OVERVIEW](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/OVERVIEW.md) 10절과 [ADR](https://github.com/kimcno3/lead-magnet-crm/blob/main/docs/adr/README.md).
