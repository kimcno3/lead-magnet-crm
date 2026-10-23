# 0012. 브랜치·환경 전략

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

혼자 3일 안에 만들지만 이슈 단위로 변경을 추적하고 리뷰할 수 있어야 한다. 환경은 장기적으로 dev와 prod 두 개를 상정하되, 이번 제출은 로컬 Docker 실행이 전부다.

## 결정

- 브랜치 두 개가 환경에 1:1로 대응한다. `main`은 prod, `develop`은 dev다.
- 이슈 하나 = 브랜치 하나 = PR 하나. 브랜치 이름은 `feat|fix|refactor|test|docs|chore/{이슈번호}-{요약}`.
- 작업 PR은 `develop`으로 squash 병합한다. 릴리스는 `develop`에서 `main`으로 가는 release PR로 병합하고 `vX.Y.Z` 태그를 단다. 마일스톤은 v0.1.0 core, v0.2.0 extras.
- 커밋과 PR 제목은 Conventional Commits(`feat(auth): refresh token 교체 적용`)를 따르고 내용은 한국어로 써도 된다.
- PR 템플릿 체크리스트: 마이그레이션 하위 호환, 보안 영향, ADR, OpenAPI, `.env.example`.
- Flyway: 이미 적용된 파일은 수정하지 않고 PR당 마이그레이션은 하나까지, 파괴적 변경은 expand → contract.
- Spring 프로필은 지금 `local`만 쓴다. `dev`·`prod` 프로필과 자동 배포(develop 병합 시 dev, main 병합 시 prod)는 [ROADMAP](../ROADMAP.md)에 있다.
- CI(`.github/workflows/ci.yml`)는 develop·main 대상 PR과 push에서 backend 빌드와 admin-web lint·typecheck·test·build를 돌린다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| Trunk 방식 + 태그 배포 | 브랜치가 하나라 단순하고 통합이 빠르다 | dev와 prod 환경이 브랜치와 대응하지 않아 어느 커밋이 어디에 떴는지 태그로 따로 추적해야 한다 | 환경과 브랜치가 1:1인 편이 배포 상태를 읽기 쉽고 release PR에서 한 번 더 검토할 지점이 생긴다 |
| 이슈 여러 개를 한 PR로 | PR 수가 줄어든다 | 리뷰 단위가 커지고 되돌리기 어렵다 | 이슈 = 브랜치 = PR이 추적에 낫다 |
| merge commit 병합 | 이력이 그대로 남는다 | develop 이력이 잡다해진다 | squash로 이슈당 커밋 하나를 유지한다 |

## 결과

- 병합 이력이 이슈 번호와 1:1로 대응한다. 대가로 develop과 main 사이의 release PR 절차가 늘어난다.
- dev·prod 배포가 생기면 프로필과 배포 파이프라인을 추가하고 이 ADR에 변경 기록을 남긴다.
