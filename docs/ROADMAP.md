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

- ~~PII 키 교체 절차.~~ v0.4.0 #67에서 완료([ADR 0022](adr/0022-pii-key-rotation.md), GUIDE 2.11): 암호화 키는 형식 2(키 id) + 온라인 재암호화, HMAC 키는 기동 시 재계산. 남은 것:
  - KMS·시크릿 매니저 연동(봉투 암호화).
  - HMAC 키 교체를 서비스 중단 없이(두 해시 컬럼 기간, expand → contract). 신청이 수백만 건이 되어 기동 시 재계산이 길어질 때.
- 배포 환경의 프록시·로드밸런서 주소를 `TRUSTED_PROXIES`에 넣는다. admin 포트의 신뢰 프록시 해석은 #51에서 넣었고(ADR 0021), 공개 포트 앞에 프록시를 둘 때는 그 ADR의 포트별 규칙을 다시 정한다.
- 감사 로그 보존기한·파기와 위변조 방지(해시 체인 또는 별도 저장소), 비정상 다운로드 알림(ADR 0016).

## 테스트

- ~~Playwright E2E를 CI에 통합.~~ v0.3.0 #54에서 완료(`.github/workflows/e2e.yml`).

## 구조

- Gradle 멀티모듈 분리(admin, public, core).
- visits 테이블 기간 파티셔닝.
