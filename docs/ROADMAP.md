# 로드맵

이번 제출 범위(v0.1.0 core, v0.2.0 extras) 밖에 두는 작업이다. 우선순위나 범위가 바뀌면 이 문서를 고친다.

## 배포

- Render 무료 서비스 4개(backend dev·prod, admin-web dev·prod)와 Neon 브랜치(dev·prod)로 환경을 운영한다.
  - develop 병합 시 dev, main 병합 시 prod에 배포한다.
- Render 구성이 어려우면 GHCR 이미지 발행까지만 한다.
- dev, prod Spring 프로필을 추가한다(현재는 local만 사용).

## 인증

- Spring Authorization Server(Authorization Code + PKCE). 현재의 Resource Server 설정은 그대로 재사용한다.
- access token 즉시 무효화(서버 세션 또는 블랙리스트).
- 로그인 시도 제한을 여러 인스턴스가 나눠 쓰는 저장소(Redis 등)로 옮기기. v0.3.0(#51)의 제한은 인메모리·인스턴스별이다(ADR 0021).
- refresh token 패밀리의 절대 수명 상한. 지금은 교체마다 14일이 다시 시작되어 계속 쓰면 세션이 끝나지 않는다(ADR 0003).
- Google SSO.

## 기능

- lead.created outbox와 웹훅(외부 CRM·알림 연동 지점).
- 일별 추이 대시보드와 배포 링크 QR 코드.
- 폼 A/B 비교.

## 보안

- PII 키 교체 절차(ADR 0015). 지금은 `LEAD_ENC_KEY`·`LEAD_HMAC_KEY`를 바꾸면 기동 시 키 확인(`pii_key_check`)에서 멈춘다.
  - 암호화 키: 암호문 버전 바이트 → 키 맵을 두고 이전 키로 읽고 새 키로 다시 암호화하는 배치, 끝나면 확인 행을 새 키로 바꾼다.
  - HMAC 키: 새 해시 컬럼과 인덱스를 더해 두 해시를 함께 쓰는 기간을 두고, 재계산이 끝나면 옛 해시를 지운다(expand → contract).
  - KMS·시크릿 매니저 연동(봉투 암호화).
- 배포 환경의 프록시·로드밸런서 주소를 `TRUSTED_PROXIES`에 넣는다. admin 포트의 신뢰 프록시 해석은 #51에서 넣었고(ADR 0021), 공개 포트 앞에 프록시를 둘 때는 그 ADR의 포트별 규칙을 다시 정한다.
- 감사 로그 보존기한·파기와 위변조 방지(해시 체인 또는 별도 저장소), 비정상 다운로드 알림(ADR 0016).

## 테스트

- ~~Playwright E2E를 CI에 통합.~~ v0.3.0 #54에서 완료(`.github/workflows/e2e.yml`).

## 구조

- Gradle 멀티모듈 분리(admin, public, core).
- visits 테이블 기간 파티셔닝.
