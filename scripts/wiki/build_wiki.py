#!/usr/bin/env python3
"""docs/의 마크다운을 GitHub 위키 페이지로 바꿔 출력 디렉터리에 쓴다.

    python3 scripts/wiki/build_wiki.py <위키 저장소 체크아웃 경로>

원본은 docs/이고 위키는 읽기용 사본이다. 문서는 저장소에서 고치고 이 스크립트로 위키를 다시 만든다.
- 페이지 이름은 한글 제목(공백은 '-')으로 바꾸고, 문서 사이 상대 링크와 저장소 blob 링크를 위키 링크로 바꾼다.
- 위키에 없는 파일(코드, openapi.yaml, e2e 결과, 구현 계획)은 main 브랜치의 저장소 주소로 바꾼다.
- Home, _Sidebar, _Footer는 여기서 만든다. 출력 디렉터리의 기존 .md는 지우고 다시 쓴다(.git은 건드리지 않는다).
"""
import re
import sys
from pathlib import Path

REPO = "https://github.com/kimcno3/lead-magnet-crm"
BLOB = f"{REPO}/blob/main"
ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"

PAGES = {
    "OVERVIEW.md": "제출-개요",
    "GUIDE.md": "사용-가이드",
    "TROUBLESHOOTING.md": "문제-해결",
    "PLAN.md": "과제-해석과-계획",
    "WORKPLAN.md": "진행-현황",
    "ROADMAP.md": "로드맵",
    "AI_FORM_PROMPT.md": "AI-폼-프롬프트",
    "adr/README.md": "ADR-목록",
}
for notes in sorted(DOCS.glob("RELEASE_NOTES_v*.md")):
    PAGES[notes.name] = "릴리스-노트-" + notes.stem.removeprefix("RELEASE_NOTES_")
ADR_TITLES = {}
for adr in sorted((DOCS / "adr").glob("0*.md")):
    if adr.name.startswith("0000"):
        continue
    number = adr.name[:4]
    PAGES[f"adr/{adr.name}"] = f"ADR-{number}"
    title = adr.read_text(encoding="utf-8").splitlines()[0].lstrip("# ").strip()
    ADR_TITLES[number] = re.sub(r"^(ADR[- ]?)?\d{4}[.:]?\s*", "", title)

LINK = re.compile(r"(!?)\[([^\]]*)\]\(([^)\s]+)\)")


def resolve(target: str, source: str) -> str:
    """source(docs 기준 경로)에 있는 링크 target을 위키 링크 또는 저장소 주소로 바꾼다."""
    if target.startswith("#") or target.startswith("mailto:"):
        return target
    path, _, anchor = target.partition("#")
    anchor = f"#{anchor}" if anchor else ""
    if path.startswith(BLOB + "/docs/") or path.startswith(f"{REPO}/blob/develop/docs/"):
        rel = path.split("/docs/", 1)[1]
    elif path.startswith("http://") or path.startswith("https://"):
        return target
    else:
        base = (DOCS / source).parent
        full = (base / path).resolve()
        try:
            rel = full.relative_to(DOCS).as_posix()
        except ValueError:
            return f"{BLOB}/{full.relative_to(ROOT).as_posix()}{anchor}"
    if rel in PAGES:
        return PAGES[rel] + anchor
    if rel.endswith("/") or (DOCS / rel).is_dir():
        rel = rel.rstrip("/")
        return f"{REPO}/tree/main/docs/{rel}{anchor}"
    return f"{BLOB}/docs/{rel}{anchor}"


def convert(text: str, source: str) -> str:
    out, in_code = [], False
    for line in text.splitlines():
        if line.lstrip().startswith("```"):
            in_code = not in_code
        if not in_code:
            line = LINK.sub(lambda m: f"{m.group(1)}[{m.group(2)}]({resolve(m.group(3), source)})", line)
        out.append(line)
    return "\n".join(out) + "\n"


def home() -> str:
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    run = readme.split("## 실행 방법", 1)[1].split("## 테스트 방법", 1)[0].strip()
    return f"""# 리드마그넷 CRM 운영 시스템

운영자가 AI로 만든 단일 HTML 신청 폼을 올려 캠페인과 채널별(인스타그램·X·유튜브·스레드) 배포 링크를 만들고, 방문자의 신청을 CRM 명단으로 모아 채널·크리에이터별 성과를 보는 시스템입니다. 글로우업리즈 개발자 채용 과제 제출물입니다.

- 저장소: {REPO}
- 최신 릴리스: {REPO}/releases/latest
- 스택: Java 21 · Spring Boot 3.5 · PostgreSQL 17 · React 19 + Vite · Docker Compose · Playwright

## 읽는 순서

| 순서 | 문서 | 이런 걸 볼 수 있습니다 |
|---|---|---|
| 1 | [제출 개요](제출-개요) | 과제를 어떻게 해석했고 무엇을 만들었는지, 보안 설계, 테스트 전략, 버전별 기능 |
| 2 | [사용 가이드](사용-가이드) | 화면별 사용법과 유스케이스. 로컬에서 직접 써 볼 때 |
| 3 | [과제 해석과 계획](과제-해석과-계획) | 요구사항 해석, 데이터 모델, 지표 정의, API, 이슈 순서 |
| 4 | [ADR 목록](ADR-목록) | 주요 결정 22건과 그 이유 |
| 5 | [진행 현황](진행-현황) | 이슈·PR 단위 진행 기록과 남은 일 |

## 직접 실행하기

저장소를 받은 뒤 루트에서 한 줄이면 됩니다(Docker 필요). `.env`가 없으면 키를 새로 만들고, 준비되면 접속 주소를 알려 줍니다.

```bash
git clone {REPO}.git && cd lead-magnet-crm
scripts/local-up.sh
```

수동으로 하려면:

{run}

막히면 [문제 해결](문제-해결)을 보세요.
"""


def sidebar() -> str:
    notes = sorted((p for p in PAGES.items() if p[0].startswith("RELEASE_NOTES")), reverse=True)
    adrs = sorted(p for p in PAGES if p.startswith("adr/0"))
    lines = [
        "**[홈](Home)**",
        "",
        "**제출물**",
        "- [제출 개요](제출-개요)",
        "- [사용 가이드](사용-가이드)",
        "- [문제 해결](문제-해결)",
        "- [AI 폼 프롬프트](AI-폼-프롬프트)",
        "",
        "**설계와 진행**",
        "- [과제 해석과 계획](과제-해석과-계획)",
        "- [진행 현황](진행-현황)",
        "- [로드맵](로드맵)",
        "",
        "**릴리스 노트**",
        *[f"- [{name.removeprefix('릴리스-노트-')}]({name})" for _, name in notes],
        "",
        "**[결정 기록(ADR)](ADR-목록)**",
        *[f"- [{PAGES[a].removeprefix('ADR-')} {ADR_TITLES[a[4:8]]}]({PAGES[a]})" for a in adrs],
    ]
    return "\n".join(lines) + "\n"


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.md"):
        old.unlink()
    for source, page in PAGES.items():
        text = (DOCS / source).read_text(encoding="utf-8")
        (out / f"{page}.md").write_text(convert(text, source), encoding="utf-8")
    (out / "Home.md").write_text(home(), encoding="utf-8")
    (out / "_Sidebar.md").write_text(sidebar(), encoding="utf-8")
    (out / "_Footer.md").write_text(
        f"이 위키는 저장소 `docs/`의 읽기용 사본입니다. 원본: [{REPO}/tree/main/docs]({REPO}/tree/main/docs)\n",
        encoding="utf-8")
    print(f"{len(PAGES) + 3}개 페이지를 {out}에 썼습니다")


if __name__ == "__main__":
    main()
