# 기여 규칙

## 브랜치

- `main`: prod. release PR로만 병합하고 `vX.Y.Z` 태그를 단다.
- `develop`: dev. 기능 PR의 대상 브랜치.
- 이슈 하나 = 브랜치 하나 = PR 하나.
- 브랜치 이름: `feat|fix|refactor|test|docs|chore/{이슈번호}-{요약}` (예: `feat/3-auth-jwt`)
- develop으로 squash 병합한다.

## 커밋과 PR 제목

[Conventional Commits](https://www.conventionalcommits.org/)를 따른다. 내용은 한국어로 써도 된다.

```
feat(auth): refresh token 교체 적용
fix(lead): 중복 신청 판정에서 대소문자 무시
```

## PR 체크리스트

PR 템플릿의 항목을 모두 확인한다.

- 마이그레이션 하위 호환 여부
- 보안 영향
- ADR 필요 여부
- OpenAPI 갱신
- .env.example 갱신

## 마이그레이션 (Flyway)

- 이미 적용된 파일은 수정하지 않는다.
- PR당 마이그레이션은 최대 하나만 넣는다.
- 파괴적 변경은 expand → contract 순서로 PR을 나눈다.

## 라벨

| 분류 | 라벨 |
|---|---|
| 종류 | `type:feat`, `type:fix`, `type:chore`, `type:docs`, `type:test` |
| 보안 영향 | `security` |
| 범위 | `scope:core`, `scope:extra` |
| 영역 | `area:*` (예: `area:auth`, `area:public`) |

## 마일스톤

- `v0.1.0 core`: 필수 요구사항
- `v0.2.0 extras`: 추가 기능

## 환경

- Spring 프로필: `local`(사용 중). `dev`, `prod`는 로드맵.
- 시크릿은 커밋하지 않는다. 새 환경 변수는 `.env.example`에 추가한다.

## 문서

- README에는 실행·테스트 방법만 쓴다.
- 나머지 문서는 `docs/`에 둔다. 결정은 `docs/adr/`에 ADR로 남긴다.
