# 0001. 기술 스택

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

3일 안에 인증, 출처 격리, 업로드 HTML 격리, 집계 지표까지 갖춘 CRM을 만들고 `docker compose up` 한 번으로 재현해야 한다. 개인정보를 다루므로 보안·트랜잭션·검증이 표준화된 생태계가 필요하다. 지표 정의는 코드에서 그대로 읽혀야 하고, 스키마는 제출물로 남아야 한다.

## 결정

| 선택 | 이유 |
|---|---|
| Java 21, Spring Boot 3.5, Gradle Kotlin DSL | 가장 익숙한 스택이고 보안·트랜잭션·검증 생태계가 표준화되어 있다 |
| Spring Security, FilterChain 여러 개 | 관리자와 공개 영역을 구조적으로 분리한다. 현재는 admin, public, 기본 세 개다(ADR 0003, 0010) |
| OAuth2 Resource Server + 자체 발급 JWT | 외부 인증 솔루션(Keycloak, Auth0)에 종속되지 않고 토큰 검증은 Spring 표준에 맡긴다. 이후 Authorization Server를 도입해도 Resource Server는 그대로 쓴다 |
| PostgreSQL | 관계형 DB 요구, jsonb, COUNT DISTINCT, 파티셔닝 지원 |
| JPA(CRUD) + 네이티브 SQL(집계) | 지표 정의가 코드에 그대로 보인다 |
| Flyway | 마이그레이션 SQL이 그대로 스키마 제출물이 된다 |
| Jsoup | HTML 파싱, 필드 추출, 정적 검사 |
| springdoc-openapi | 코드와 문서가 어긋나지 않는다. `docs/api/openapi.yaml`은 실행 중인 앱에서 내보낸다 |
| JUnit 5, Testcontainers, RestAssured | 실제 Postgres로 검증한다. H2는 쓰지 않는다 |
| 단일 앱 + 포트 두 개 | 출처 단위로 격리하면서 3일 안에 끝낼 수 있다(ADR 0010) |
| sandbox iframe + postMessage | AI가 만든 HTML의 표현력은 살리고 권한은 0으로 만든다(ADR 0002) |
| React + Vite + TypeScript (admin-web) | 메모리 토큰 SPA 구조와 맞는다. 공개 폼 래퍼는 별도 vanilla JS(ADR 0011) |
| Docker Compose 로컬 실행 | 명령 하나로 재현할 수 있다. 실제 배포는 로드맵으로 미룬다 |

**버전 고정**
- Spring Boot는 3.5.16이다. Spring Initializr가 더 이상 3.x를 내려주지 않아 직접 고정했다.
- Gradle 8.14.3(wrapper)을 쓰고, Java 툴체인은 JDK 21이다. 실행하는 JDK는 17 이상이면 되고 툴체인이 JDK 21을 받는다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| Node 백엔드 | 프론트와 언어 통일 | 보안·트랜잭션 구성을 직접 조립해야 한다 | Spring 쪽이 더 익숙하고 표준 컴포넌트가 많다 |
| 직접 만든 인터셉터로 영역 분리 | 의존성이 적다 | 검증된 보안 필터 체인이 아니다 | Spring Security 체인이 구조적 분리에 맞다 |
| password grant, 세션 쿠키 | 구현이 단순 | password grant는 OAuth 2.1에서 폐기, 세션은 CSRF·저장소 부담 | 자체 JWT + refresh 교체로 간다(ADR 0003) |
| MySQL | 널리 쓰임 | jsonb, 부분 유니크 인덱스 등이 약하다 | PostgreSQL이 요구에 더 맞다 |
| 전부 JPQL | 도구 하나 | 지표 정의가 쿼리 빌더에 가려진다 | 집계는 네이티브 SQL |
| `ddl-auto` | 빠르다 | 스키마 제출물이 없고 변경 이력이 남지 않는다 | Flyway |
| 정규식 HTML 파싱 | 의존성 없음 | 깨지기 쉽다 | Jsoup |
| 수기 API 문서 | 자유로운 서술 | 코드와 어긋난다 | springdoc |
| H2 테스트 | 빠르다 | 운영 DB와 동작이 다르다(jsonb, 부분 인덱스) | Testcontainers |
| Gradle 멀티모듈 | 경계가 코드 수준으로 강제된다 | 3일 범위에 과하다 | 로드맵으로 미룬다 |
| HTML sanitize | 구현이 짧다 | 우회 사례가 계속 나오고 스크립트에 기대는 폼이 깨진다 | sandbox iframe |
| Thymeleaf 관리자 화면 | 프론트 빌드가 없다 | 메모리 토큰 SPA 구조와 맞지 않는다 | React + Vite |

## 결과

- 한 언어·한 앱으로 보안 경계를 테스트로 증명하기 쉽다.
- Testcontainers와 Docker가 있어야 backend 테스트가 돈다.
- 스택 선택을 바꾸는 큰 변경(멀티모듈 분리, Authorization Server 도입)은 [ROADMAP](../ROADMAP.md)에 있다.
