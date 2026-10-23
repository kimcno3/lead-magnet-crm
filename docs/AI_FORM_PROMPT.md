# AI 폼 프롬프트

AI(ChatGPT, Claude 등)에게 신청 폼 HTML을 만들게 할 때 그대로 붙여 넣는 프롬프트다. 이 규칙대로 만들면 업로드 검사에서 오류도 경고도 나오지 않는다. 관리자 화면 **템플릿** 목록의 **AI 프롬프트 복사** 버튼은 아래 프롬프트와 같은 글을 복사한다(테스트가 두 글이 같은지 확인한다).

사용법:

1. 아래 프롬프트를 복사해 AI에게 보낸다.
2. 이어서 원하는 디자인과 문구를 적는다(예: "무료 마케팅 가이드 신청 폼, 이름·휴대폰·이메일·회사 규모를 받는다").
3. AI가 준 HTML을 `.html` 파일로 저장해 **템플릿 → HTML 업로드**에서 **검사만 하기**를 먼저 눌러 본다.
4. 경고가 나오면 경고 문구를 그대로 AI에게 붙여 넣고 고쳐 달라고 한다. 경고 코드별 뜻은 [TROUBLESHOOTING 3절](TROUBLESHOOTING.md#3-템플릿-업로드-오류-코드별)에 있다.

## 프롬프트

```text
신청(리드 수집) 폼을 단일 HTML 파일 하나로 만들어 줘. 아래 규칙을 모두 지켜 줘.

[파일]
- 설명 없이 <!doctype html>부터 </html>까지 HTML만 출력해. UTF-8, 512KB 이하.
- CSS는 <style> 태그 안에, JavaScript는 <script> 태그 안에 직접 써. 별도 파일은 만들지 마.

[폼 구조]
- <form>은 문서 전체에 정확히 하나만 둬. form에 action 속성은 쓰지 마. 제출 버튼은 form 안의 <button type="submit">.
- 모든 입력에 name 속성을 붙여. name은 영문자로 시작하고 영문·숫자·밑줄만 쓴 50자 이하, 입력은 최대 50개.
- 이름은 name="name", 전화번호는 name="phone"(type="tel"), 이메일은 name="email"(type="email")로 해. phone과 email 중 하나 이상은 반드시 넣고, 그중 최소 하나에는 required 속성을 붙여.
- 연락처 입력은 name="phone" 하나, name="email" 하나만 둬. 보조 전화번호나 보조 이메일처럼 type="tel"·type="email" 입력이나 이름에 phone, tel, mobile, hp, email, mail이 들어간 입력을 더 만들지 마.
- 여러 개 중 고르는 입력은 select, radio, checkbox를 써. radio·checkbox 묶음은 같은 name을 공유하고 각각 value를 줘.
- 모든 입력에 <label for="id">를 연결하거나 입력을 <label>로 감싸. placeholder만 있는 입력은 안 돼.
- type="file", type="password" 입력은 쓰지 마.
- 개인정보 수집 동의 체크박스나 약관 문구는 넣지 마. 동의는 시스템이 폼 아래에 따로 붙여.

[외부 리소스 금지]
- 공개 폼은 외부 주소의 리소스를 전부 차단해. 아래는 쓰지 마.
  - <script src="https://...">, <link rel="stylesheet" href="https://...">, CSS의 @import
  - 외부 주소의 이미지(<img src="https://...">, CSS의 url(https://...)), 외부 아이콘
  - 웹폰트(Google Fonts, 구독형 폰트 CDN, @font-face의 외부 url). 폰트는 시스템 폰트 목록(font-family: system-ui, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif)만 써.
  - Tailwind CDN, Bootstrap CDN 같은 CDN 전부
- 이미지가 꼭 필요하면 data: URI로 넣고, 이미지 전체 합계를 200KB 이하로 유지해. 가능하면 CSS 그라데이션과 이모지로 꾸며.

[스크립트]
- 입력 마스킹, 글자 수 세기 같은 화면 동작용 인라인 스크립트는 써도 돼.
- fetch, XMLHttpRequest, navigator.sendBeacon, WebSocket, EventSource는 쓰지 마. 제출은 시스템이 처리하니 직접 서버로 보내는 코드를 넣지 마.
```

## 규칙과 검사 코드 대응표

| 규칙 | 어기면 | 종류 |
|---|---|---|
| 파일은 `.html`, UTF-8, 512KB 이하 | `template_invalid_extension`, `template_not_utf8`, `template_empty`, `template_too_large` | 오류(업로드 거부) |
| `<form>`은 정확히 하나 | `template_form_count_invalid` | 오류 |
| `type="file"`, `type="password"` 금지 | `template_forbidden_field` | 오류 |
| `name` 규칙, 50개 이하, 같은 이름은 radio·checkbox끼리만 | `template_field_invalid` | 오류 |
| `phone` 또는 `email` 입력 필요 | `template_contact_field_missing` | 오류 |
| phone·email 중 하나 이상 `required` | `contact_not_required` | 경고 |
| 모든 입력에 label | `missing_label` | 경고 |
| 외부 `<script src>` 금지 | `external_script` | 경고 |
| 외부 CSS(`<link rel=stylesheet>`, `@import`) 금지 | `external_stylesheet` | 경고 |
| 외부 이미지·아이콘 금지(`data:` URI 사용) | `external_image` | 경고 |
| 외부 웹폰트 금지 | `external_font` | 경고 |
| `data:` URI 합계 200KB 이하 | `large_inline_image` | 경고 |
| form에 `action` 쓰지 않기 | `form_action_external` | 참고 |
| `fetch`·`XMLHttpRequest`·`sendBeacon`·`WebSocket`·`EventSource` 쓰지 않기 | `fetch_in_script` | 참고 |
| 연락처 입력은 `phone`·`email` 하나씩만 | `extra_contact_field` | 경고 |
| 동의 체크박스 넣지 않기 | `consent_in_html` | 참고 |

오류는 업로드를 막고, 경고와 참고는 업로드를 막지 않는다. 경고는 공개 폼에서 실제로 문제가 생길 수 있는 것이고, 참고는 알아 두면 되는 것이다.

## 검사를 통과하는 출력 예시

경고가 하나도 나오지 않는 최소 예시다(테스트가 이 코드를 실제로 검사한다).

```html
<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>무료 가이드 신청</title>
<style>
  body { margin: 0; padding: 16px; font-family: system-ui, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif; color: #1f2330; }
  form { display: grid; gap: 14px; max-width: 420px; margin: 0 auto; }
  label { font-weight: 600; font-size: 14px; }
  input, select { width: 100%; padding: 12px; font: inherit; border: 1px solid #ccc; border-radius: 8px; box-sizing: border-box; }
  button { padding: 14px; font: inherit; font-weight: 700; color: #fff; background: #1f2330; border: 0; border-radius: 10px; }
</style>
</head>
<body>
  <form id="lead-form">
    <div>
      <label for="f-name">이름</label>
      <input id="f-name" type="text" name="name" autocomplete="name" required>
    </div>
    <div>
      <label for="f-phone">휴대폰 번호</label>
      <input id="f-phone" type="tel" name="phone" placeholder="010-0000-0000" autocomplete="tel" required>
    </div>
    <div>
      <label for="f-email">이메일</label>
      <input id="f-email" type="email" name="email" autocomplete="email">
    </div>
    <div>
      <label for="f-size">회사 규모</label>
      <select id="f-size" name="company_size">
        <option value="solo">1인</option>
        <option value="small">2~10명</option>
        <option value="large">11명 이상</option>
      </select>
    </div>
    <button type="submit">무료 가이드 받기</button>
  </form>
  <script>
    (function () {
      var phone = document.getElementById('f-phone');
      phone.addEventListener('input', function () {
        var d = phone.value.replace(/\D/g, '').slice(0, 11);
        phone.value = d.length < 4 ? d : d.length < 8 ? d.slice(0, 3) + '-' + d.slice(3) : d.slice(0, 3) + '-' + d.slice(3, 7) + '-' + d.slice(7);
      });
    })();
  </script>
</body>
</html>
```

관련 문서: [GUIDE 2.2절](GUIDE.md#22-신청-폼-html-준비하기-ai에게-만들게-할-때), [ADR 0004](adr/0004-html-form-contract.md), [ADR 0002](adr/0002-html-isolation.md), [ADR 0017](adr/0017-template-inspection.md).
