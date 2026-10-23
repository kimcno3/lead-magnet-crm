/** 운영자가 AI에게 붙여 넣는 폼 생성 프롬프트. docs/AI_FORM_PROMPT.md의 첫 text 코드 블록과 같아야 한다(aiFormPrompt.test.ts가 확인). */
export const AI_FORM_PROMPT = `신청(리드 수집) 폼을 단일 HTML 파일 하나로 만들어 줘. 아래 규칙을 모두 지켜 줘.

[파일]
- 설명 없이 <!doctype html>부터 </html>까지 HTML만 출력해. UTF-8, 512KB 이하.
- CSS는 <style> 태그 안에, JavaScript는 <script> 태그 안에 직접 써. 별도 파일은 만들지 마.

[폼 구조]
- <form>은 문서 전체에 정확히 하나만 둬. form에 action 속성은 쓰지 마. 제출 버튼은 form 안의 <button type="submit">.
- 모든 입력에 name 속성을 붙여. name은 영문자로 시작하고 영문·숫자·밑줄만 쓴 50자 이하, 입력은 최대 50개.
- 이름은 name="name", 전화번호는 name="phone"(type="tel"), 이메일은 name="email"(type="email")로 해. phone과 email 중 하나 이상은 반드시 넣고, 그중 최소 하나에는 required 속성을 붙여.
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
- fetch, XMLHttpRequest, navigator.sendBeacon, WebSocket, EventSource는 쓰지 마. 제출은 시스템이 처리하니 직접 서버로 보내는 코드를 넣지 마.`
