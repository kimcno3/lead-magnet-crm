#!/usr/bin/env bash
# GitHub 라벨, 마일스톤, 이슈(core 12개, extras 7개)를 만든다.
# 여러 번 실행해도 안전하다: 이미 있는 라벨은 갱신하고, 같은 제목의 마일스톤·이슈는 건너뛴다.
# 사용법: scripts/bootstrap-github.sh [owner/repo]   (기본값: 현재 디렉터리의 gh 기본 저장소)
set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
echo "대상 저장소: $REPO"

# ---------- 라벨 ----------
label() { # name color description
  if gh api "repos/$REPO/labels/$(jq -rn --arg s "$1" '$s|@uri')" >/dev/null 2>&1; then
    gh api -X PATCH "repos/$REPO/labels/$(jq -rn --arg s "$1" '$s|@uri')" \
      -f color="$2" -f description="$3" >/dev/null
    echo "  라벨 갱신: $1"
  else
    gh api "repos/$REPO/labels" -f name="$1" -f color="$2" -f description="$3" >/dev/null
    echo "  라벨 생성: $1"
  fi
}

echo "라벨"
label "type:feat"     "1d76db" "새 기능"
label "type:fix"      "d73a4a" "버그 수정"
label "type:chore"    "c5def5" "빌드, 설정, 저장소 관리"
label "type:docs"     "0075ca" "문서"
label "type:test"     "bfd4f2" "테스트"
label "security"      "b60205" "보안 영향이 있는 변경"
label "scope:core"    "0e8a16" "v0.1.0 필수 요구사항"
label "scope:extra"   "fbca04" "v0.2.0 추가 기능"
label "area:infra"    "ededed" "저장소, CI, Docker"
label "area:db"       "ededed" "스키마, 마이그레이션"
label "area:auth"     "ededed" "인증"
label "area:template" "ededed" "HTML 템플릿"
label "area:campaign" "ededed" "캠페인, 폼"
label "area:link"     "ededed" "배포 링크"
label "area:public"   "ededed" "공개 폼, 출처 격리"
label "area:lead"     "ededed" "신청, 명단"
label "area:stats"    "ededed" "성과 지표"
label "area:admin-web" "ededed" "관리자 UI"
label "area:docs"     "ededed" "문서, ADR"

# ---------- 마일스톤 ----------
milestone_number() { # title -> number (없으면 빈 문자열)
  gh api "repos/$REPO/milestones?state=all&per_page=100" \
    --jq ".[] | select(.title == \"$1\") | .number"
}

milestone() { # title description
  local n
  n="$(milestone_number "$1")"
  if [ -z "$n" ]; then
    n="$(gh api "repos/$REPO/milestones" -f title="$1" -f description="$2" --jq .number)"
    echo "  마일스톤 생성: $1 (#$n)" >&2
  else
    echo "  마일스톤 존재: $1 (#$n)" >&2
  fi
  echo "$n"
}

echo "마일스톤"
M_CORE="$(milestone "v0.1.0 core" "필수 요구사항")"
M_EXTRA="$(milestone "v0.2.0 extras" "추가 기능")"

# ---------- 이슈 ----------
EXISTING="$(gh api --paginate "repos/$REPO/issues?state=all&per_page=100" --jq '.[].title')"

issue() { # milestone title body labels...
  local m="$1" title="$2" body="$3"
  shift 3
  if printf '%s\n' "$EXISTING" | grep -Fxq -- "$title"; then
    echo "  이슈 존재: $title"
    return
  fi
  local args=(-f title="$title" -f body="$body" -F milestone="$m")
  local l
  for l in "$@"; do args+=(-f "labels[]=$l"); done
  local n
  n="$(gh api "repos/$REPO/issues" "${args[@]}" --jq .number)"
  echo "  이슈 생성: #$n $title"
}

DONE_CORE="완료 조건: 성공·실패 테스트 통과, 관련 ADR 추가, PR 템플릿 체크리스트 작성. 계획은 docs/PLAN.md 6.1절."
DONE_EXTRA="완료 조건: 성공·실패 테스트 통과, 관련 ADR 추가, PR 템플릿 체크리스트 작성. 계획은 docs/PLAN.md 6.2절."

echo "이슈 (core)"
issue "$M_CORE" "chore: 저장소 골격과 로컬 Docker 실행 환경" \
"backend(Spring Boot 3, Java 21, Gradle Kotlin DSL, 헬스체크, 8080·8081 포트 자리), admin-web(React + Vite + TypeScript, lint·typecheck·test), docker-compose(postgres, backend, admin-web), .env.example, GitHub Actions CI.

$DONE_CORE" type:chore scope:core area:infra

issue "$M_CORE" "feat(db): Flyway V1 스키마, 프로필 분리, 운영자 시드" \
"operators, refresh_tokens, html_templates, campaigns, forms, distribution_links, visits, leads 테이블. local 프로필 분리. 운영자 계정은 환경 변수로 시드.

$DONE_CORE" type:feat scope:core area:db

issue "$M_CORE" "feat(auth): JWT 발급, refresh 교체·재사용 감지, Resource Server 설정" \
"로그인, refresh, logout, me. access token은 응답 바디, refresh token은 HttpOnly·SameSite=Strict·Path=/api/admin/auth 쿠키. 사용할 때마다 교체하고 재사용 감지 시 패밀리 폐기. 미인증·잘못된 비밀번호·만료·재사용 실패 테스트.

$DONE_CORE" type:feat scope:core area:auth security

issue "$M_CORE" "feat(template): HTML 업로드와 Jsoup 필드 추출" \
"단일 .html 업로드, 크기·MIME·빈 파일 검증, sha256, Jsoup으로 폼 필드 추출.

$DONE_CORE" type:feat scope:core area:template security

issue "$M_CORE" "feat(campaign): 캠페인 CRUD, 템플릿 기반 폼 생성, 게시·마감" \
"캠페인 생성·조회·수정, 템플릿으로 폼 생성, draft → published → closed 상태 전이.

$DONE_CORE" type:feat scope:core area:campaign

issue "$M_CORE" "feat(link): 4개 채널 배포 링크와 /l/{code} 리다이렉트" \
"instagram, x, youtube, threads 링크 생성. /l/{code} 방문 기록 후 /f/{slug}?lm={code}로 302. 존재하지 않는 code 실패 테스트.

$DONE_CORE" type:feat scope:core area:link

issue "$M_CORE" "feat(public): 8081 커넥터, OriginGuardFilter, 샌드박스 래퍼, 방문 추적" \
"public 포트, OriginGuardFilter와 FilterChain 분리, sandbox iframe(allow-scripts allow-forms) + postMessage 래퍼, CSP, visitor 쿠키, 방문 추적. 격리 테스트.

$DONE_CORE" type:feat scope:core area:public security

issue "$M_CORE" "feat(lead): 신청 제출 검증, 채널 귀속, 명단 조회, 중복 신청 처리" \
"필수 필드·형식 검증, 게시 전·마감 폼 거부, 채널 귀속, 명단 조회, 중복 신청 정책.

$DONE_CORE" type:feat scope:core area:lead security

issue "$M_CORE" "feat(stats): 캠페인·채널 지표와 기간 필터" \
"방문, 방문자, 신청, 전환율, 채널별 성과. 네이티브 SQL 집계. 기간 필터.

$DONE_CORE" type:feat scope:core area:stats

issue "$M_CORE" "test: 로그인부터 지표 검증까지 성공·실패 시나리오" \
"로그인 → 업로드 → 캠페인 → 폼 게시 → 링크 → 방문 → 신청 → 지표 숫자 검증. 실패·격리 시나리오 묶음.

$DONE_CORE" type:test scope:core

issue "$M_CORE" "feat(admin-web): 로그인, 업로드·미리보기, 캠페인·폼·링크, 대시보드, 명단" \
"메모리 토큰 SPA. 업로드 HTML 미리보기도 sandbox iframe으로만 렌더링.

$DONE_CORE" type:feat scope:core area:admin-web security

issue "$M_CORE" "docs: ADR 전체, OVERVIEW, OpenAPI, README 확정, v0.1.0 릴리스" \
"docs/adr 전체, docs/OVERVIEW.md, docs/api/openapi.yaml, README 실행·테스트 방법 확정, release PR과 v0.1.0 태그.

$DONE_CORE" type:docs scope:core area:docs

echo "이슈 (extras)"
issue "$M_EXTRA" "feat(creator): 크리에이터별 성과 추적" \
"creators 테이블, 링크를 크리에이터 × 채널 단위로 생성, 크리에이터별 지표.

$DONE_EXTRA" type:feat scope:extra area:stats

issue "$M_EXTRA" "feat(lead): 개인정보 암호화와 마스킹, 원문 열람 API 분리" \
"AES-GCM 암호화와 HMAC 해시(중복 판정용), 조회 시 마스킹, 원문 열람 API 분리.

$DONE_EXTRA" type:feat scope:extra area:lead security

issue "$M_EXTRA" "feat(audit): 명단 열람·다운로드 감사 로그" \
"누가 언제 어떤 명단을 열람·다운로드했는지 기록.

$DONE_EXTRA" type:feat scope:extra area:lead security

issue "$M_EXTRA" "feat(template): 업로드 HTML 검사 리포트와 AI 프롬프트 가이드" \
"폼 계약 체크, 외부 전송·외부 스크립트 경고, 운영자용 AI 프롬프트 가이드.

$DONE_EXTRA" type:feat scope:extra area:template security

issue "$M_EXTRA" "feat(campaign): 게시 시 템플릿 스냅샷" \
"게시 시점의 템플릿을 고정해 이후 템플릿 변경이 게시된 폼에 영향을 주지 않게 한다.

$DONE_EXTRA" type:feat scope:extra area:campaign

issue "$M_EXTRA" "feat(public): 데이터 품질 방어(honeypot, rate limit, 봇 방문 제외)" \
"honeypot 필드, 제출 rate limit, 봇 user agent 방문 제외.

$DONE_EXTRA" type:feat scope:extra area:public security

issue "$M_EXTRA" "feat(lead): 보존기한 파기(익명화)와 동의 문구 버전 기록" \
"보존기한이 지난 신청 익명화, 신청 시점의 동의 문구 버전 기록.

$DONE_EXTRA" type:feat scope:extra area:lead security

echo "완료"
