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
- Google SSO.

## 기능

- lead.created outbox와 웹훅(외부 CRM·알림 연동 지점).
- 일별 추이 대시보드와 배포 링크 QR 코드.
- 폼 A/B 비교.

## 구조

- Gradle 멀티모듈 분리(admin, public, core).
- visits 테이블 기간 파티셔닝.
