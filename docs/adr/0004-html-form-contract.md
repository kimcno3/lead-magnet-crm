# 0004. HTML 폼 계약

- 상태: 승인
- 날짜: 2026-10-23

## 맥락

운영자는 AI로 만든 단일 `.html` 폼 파일을 올리고, 이후 캠페인의 공개 폼이 이 파일로 만들어진다. 업로드 파일은 신뢰할 수 없는 입력이다. 서버가 폼 구조를 이해하려면(필드 목록, 신청 검증, 연락처 확보) 파일이 지켜야 할 최소 계약이 필요하다. 연락처 규칙은 `leads`의 `ck_leads_contact`(phone 또는 email 필수, ADR 0008)와 맞아야 한다.

## 결정

**파일**
- 단일 `.html`/`.htm`(대소문자 무시). multipart 파트의 Content-Type은 신뢰하지 않고 확장자와 내용으로 판단한다.
- UTF-8 텍스트이고 비어 있지 않아야 한다. 엄격한 디코더(잘못된 바이트는 오류)로 읽고, UTF-8 BOM은 제거한다. PostgreSQL `text`에 저장할 수 없는 NUL 문자가 있으면 같은 이유(`template_not_utf8`)로 거부한다.
- 크기 한도는 두 겹이다. `app.template.max-bytes`(524288, 512KB)를 넘으면 애플리케이션이 413 `template_too_large`로 막는다. multipart 한도 `spring.servlet.multipart.max-file-size`·`max-request-size`는 1MB이고, 넘으면 `MaxUploadSizeExceededException`이 413 `payload_too_large`가 된다. 1MB를 넘는 본문을 서버가 끝까지 받지 않게 하려는 바깥 방어선이다.

**form과 필드**
- 문서 전체에서 `<form>`이 정확히 하나여야 한다(`template_form_count_invalid`).
- 필드는 form 안의 `input`, `select`, `textarea` 중 `name` 속성이 있는 것이다. `input type`이 `submit|button|reset|image`이면 필드가 아니다.
- `type=file`, `type=password`는 거부한다(`template_forbidden_field`). 파일 업로드와 비밀번호 수집은 이 제품의 범위가 아니다.
- 필드 이름은 `^[a-zA-Z][a-zA-Z0-9_]{0,49}$`, 최대 50개. 같은 이름은 `radio`끼리 또는 `checkbox`끼리만 허용하며 하나의 필드로 묶고 `options`에 value를 모은다. 위반은 `template_field_invalid`.
- 예약 필드는 `name`(이름), `phone`(전화번호), `email`(이메일)이다. `phone` 또는 `email` 중 하나는 반드시 있어야 한다(`template_contact_field_missing`).

**추출 결과** `fields`(jsonb): `[{name, type, required, label, options}]`
- `type`: `text|email|tel|number|date|textarea|select|radio|checkbox|hidden|url` 중 하나로 정규화한다. 그 밖의 input type(`color`, `range` 등)과 type 생략은 `text`.
- `required`: `required` 속성 유무(묶은 필드는 하나라도 있으면 true).
- `label`: `<label for=id>` 텍스트 → 감싼 `<label>` 텍스트 → `placeholder` → `name` 순서.
- `options`: select는 `option`의 `value`(없으면 option 텍스트), radio·checkbox는 `value` 속성 목록. 해당 없으면 빈 배열.

**스크립트·스타일·외부 리소스는 막지 않는다.** 정제(sanitize)로 막으면 AI가 만든 폼이 깨지고 우회도 쉽다. 대신 업로드 HTML은 `allow-same-origin` 없는 sandbox iframe 안에서만 실행해 관리자·공개 출처와 격리한다(ADR 0002에서 이유와 한계를 다룬다). `<form action>`은 무시된다. 래퍼가 제출을 가로채 postMessage로 받은 값을 검증한 뒤 public API로 보낸다.

**저장**: `html_templates`에 원문(`content`), 바이트 크기(`size`), SHA-256(`sha256`, 같은 파일인지 확인용), 추출한 `fields`, 올린 운영자(`created_by`, JWT `sub`)를 저장한다. 템플릿은 수정하지 않고 새로 올린다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택하지 않은 이유 |
|---|---|---|---|
| HTML 정제(스크립트·스타일 제거) | 업로드 시점에 위험 제거 | AI 생성 폼의 동작·스타일이 깨진다. 정제기 우회 위험이 남는다 | 렌더링 격리(sandbox)가 더 강한 경계다 |
| 계약 없이 저장만 하고 렌더링 때 파싱 | 업로드가 쉽다 | 잘못된 파일이 캠페인 단계에서야 발견된다 | 업로드 시점에 빨리 실패시킨다 |
| 필드 이름 중복 전부 거부 | 규칙이 단순하다 | radio·checkbox 그룹을 표현할 수 없다 | HTML 표준 사용 방식을 허용한다 |
| 파일 크기 한도를 multipart 하나로 | 설정이 하나다 | 한도 근처의 오류가 불명확하고 값이 1MB로 고정된다 | 512KB 업무 한도와 1MB 전송 한도를 분리한다 |
| Content-Type으로 HTML 판별 | 구현이 쉽다 | 클라이언트가 마음대로 정한다 | 확장자와 내용으로 검증한다 |

## 결과

- 계약을 어긴 파일은 업로드 시점에 422로 걸러지고, 이후 단계(폼 생성, 신청 검증)는 저장된 `fields`를 믿고 쓸 수 있다.
- 필드 라벨 추출은 휴리스틱이다. radio·checkbox 묶음의 라벨은 첫 요소 기준이라 운영자가 이후 폼 설정(`field_config`)에서 고칠 수 있어야 한다.
- 중첩 form이나 잘못된 HTML은 jsoup(HTML5 파서)이 브라우저처럼 보정한 결과를 기준으로 판단한다.
- 스크립트를 허용하므로 격리가 깨지면 곧바로 위험해진다. 격리 불변식(CLAUDE.md 보안 불변식)의 변경은 별도 ADR과 승인이 필요하다.
