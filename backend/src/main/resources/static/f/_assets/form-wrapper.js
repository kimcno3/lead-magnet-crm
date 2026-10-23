/*
 * 공개 폼 래퍼 (ADR 0002). React와 분리된 vanilla JS.
 *
 * 1. 슬러그는 location.pathname(/f/{slug})에서 읽고 /api/public/forms/{slug}를 가져온다.
 * 2. 업로드 HTML에 브리지 스크립트를 붙여 sandbox="allow-scripts allow-forms" iframe의 srcdoc으로 넣는다.
 *    iframe은 불투명 출처(origin "null")라 이 페이지의 DOM, 쿠키, 저장소에 닿지 못한다.
 * 3. 브리지가 보낸 lm:submit 메시지만 받아 필드와 동의를 검증한 뒤 공개 제출 API로 보낸다.
 * 서버나 업로드 HTML에서 온 값은 textContent와 srcdoc 속성으로만 넣는다(innerHTML 금지).
 *
 * 데이터 품질 방어(ADR 0019):
 * - 브리지가 폼에 사람에게 보이지 않는 honeypot 입력 _lm_trap을 넣는다. 래퍼는 제출 fields에 _lm_trap을 항상 싣는다
 *   (사람이면 빈 문자열). 밑줄로 시작하는 이름은 템플릿 필드 이름 규칙이 허용하지 않아 운영자 필드와 겹치지 않는다.
 * - 폼 조회 응답의 작성 시간 토큰 ft를 제출에 그대로 싣는다. 서버가 422 invalid_form_token으로 거절하면
 *   안내를 보여 주고 토큰을 새로 받아, 사람이 잠시 뒤 다시 제출하면 통과하게 한다.
 */
(function () {
  "use strict";

  var SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/;
  var MAX_FRAME_HEIGHT = 10000;
  var HONEYPOT = "_lm_trap";

  // iframe 안에서 도는 코드. 업로드 HTML과 같은 문서에 있으므로 이 스크립트가 보내는 값도 믿지 않고 래퍼에서 검증한다.
  var BRIDGE = "<script>(function () {\n" +
    "  'use strict';\n" +
    "  var form = document.querySelector('form');\n" +
    "  if (form) {\n" +
    "    var trap = document.createElement('input');\n" +
    "    trap.type = 'text'; trap.name = '_lm_trap'; trap.value = '';\n" +
    "    trap.setAttribute('autocomplete', 'off'); trap.setAttribute('tabindex', '-1'); trap.setAttribute('aria-hidden', 'true');\n" +
    "    var hide = { position: 'absolute', left: '-10000px', top: 'auto', width: '1px', height: '1px',\n" +
    "      overflow: 'hidden', opacity: '0', 'pointer-events': 'none' };\n" +
    "    Object.keys(hide).forEach(function (p) { trap.style.setProperty(p, hide[p], 'important'); });\n" +
    "    form.appendChild(trap);\n" +
    "  }\n" +
    "  function reportHeight() {\n" +
    "    parent.postMessage({ type: 'lm:resize', height: document.documentElement.scrollHeight }, '*');\n" +
    "  }\n" +
    "  if (form) {\n" +
    "    form.addEventListener('submit', function (event) {\n" +
    "      event.preventDefault();\n" +
    "      var fields = {};\n" +
    "      new FormData(form).forEach(function (value, name) {\n" +
    "        if (typeof value !== 'string') { return; }\n" +
    "        if (Object.prototype.hasOwnProperty.call(fields, name)) {\n" +
    "          fields[name] = [].concat(fields[name], value);\n" +
    "        } else {\n" +
    "          fields[name] = value;\n" +
    "        }\n" +
    "      });\n" +
    "      parent.postMessage({ type: 'lm:submit', fields: fields }, '*');\n" +
    "    });\n" +
    "  }\n" +
    "  window.addEventListener('message', function (event) {\n" +
    "    if (event.source !== parent || !event.data || event.data.type !== 'lm:result') { return; }\n" +
    "    if (event.data.ok && form) { form.reset(); }\n" +
    "  });\n" +
    "  window.addEventListener('load', reportHeight);\n" +
    "  if (window.ResizeObserver) { new ResizeObserver(reportHeight).observe(document.documentElement); }\n" +
    "})();<\/script>";

  var titleEl = document.getElementById("lm-title");
  var statusEl = document.getElementById("lm-status");
  var cardEl = document.getElementById("lm-form-card");
  var frame = document.getElementById("lm-frame");
  var consentArea = document.getElementById("lm-consent-area");
  var consentBox = document.getElementById("lm-consent");
  var consentText = document.getElementById("lm-consent-text");
  var resultEl = document.getElementById("lm-result");

  var slug = readSlug();
  var form = null;
  var formToken = null;
  var submitting = false;

  function readSlug() {
    var parts = location.pathname.split("/");
    if (parts.length !== 3 || parts[1] !== "f") { return null; }
    var value;
    try {
      value = decodeURIComponent(parts[2]);
    } catch (e) {
      return null;
    }
    return SLUG_PATTERN.test(value) ? value : null;
  }

  function lmParam() {
    return new URLSearchParams(location.search).get("lm");
  }

  function setStatus(text) {
    statusEl.textContent = text;
  }

  function showResult(ok, message, details) {
    while (resultEl.firstChild) { resultEl.removeChild(resultEl.firstChild); }
    resultEl.className = "result " + (ok ? "ok" : "error");
    var p = document.createElement("p");
    p.textContent = message;
    resultEl.appendChild(p);
    if (details && details.length) {
      var ul = document.createElement("ul");
      details.forEach(function (detail) {
        var li = document.createElement("li");
        li.textContent = detail;
        ul.appendChild(li);
      });
      resultEl.appendChild(ul);
    }
  }

  // 마지막 </body>(대소문자·닫는 꺾쇠 앞 공백 무관) 앞에 브리지를 넣는다. 없으면 끝에 붙인다.
  function withBridge(html) {
    var pattern = /<\/body\s*>/gi;
    var index = -1;
    var match;
    while ((match = pattern.exec(html)) !== null) {
      index = match.index;
    }
    return index < 0 ? html + BRIDGE : html.slice(0, index) + BRIDGE + html.slice(index);
  }

  function fieldByName(name) {
    for (var i = 0; i < form.fields.length; i++) {
      if (form.fields[i].name === name) { return form.fields[i]; }
    }
    return null;
  }

  function labelOf(name) {
    var field = fieldByName(name);
    return field && field.label ? field.label : name;
  }

  function isFilled(value) {
    if (Array.isArray(value)) {
      return value.some(function (v) { return v.trim() !== ""; });
    }
    return typeof value === "string" && value.trim() !== "";
  }

  // 브리지가 보낸 값은 업로드 HTML이 조작할 수 있으므로 fieldConfig 기준으로 다시 확인한다.
  function validate(fields) {
    var errors = [];
    if (!fields || typeof fields !== "object" || Array.isArray(fields)) {
      return { errors: ["입력값을 읽을 수 없습니다."] };
    }
    var clean = {};
    var honeypot = "";
    Object.keys(fields).forEach(function (name) {
      if (name === HONEYPOT) {
        // 사람에게는 보이지 않는 입력이라 값이 있으면 봇이다. 판단은 서버가 하므로 값을 그대로 넘긴다.
        honeypot = [].concat(fields[name]).join("");
        return;
      }
      var field = fieldByName(name);
      var value = fields[name];
      var valid = typeof value === "string" ||
        (Array.isArray(value) && value.every(function (v) { return typeof v === "string"; }));
      if (!field || !valid) {
        errors.push("알 수 없는 입력 항목이 있습니다: " + name);
        return;
      }
      clean[name] = field.type === "checkbox" && typeof value === "string" ? [value] : value;
    });
    form.fields.forEach(function (field) {
      if (field.required && !isFilled(clean[field.name])) {
        errors.push(labelOf(field.name) + " 항목을 입력해 주세요.");
      }
    });
    clean[HONEYPOT] = honeypot;
    return { errors: errors, fields: clean };
  }

  function replyToFrame(ok) {
    // sandbox iframe은 불투명 출처라 targetOrigin을 지정할 수 없다. 대상 창은 이 iframe으로 고정한다.
    frame.contentWindow.postMessage({ type: "lm:result", ok: ok }, "*");
  }

  function problemMessages(problem) {
    if (!problem || !Array.isArray(problem.errors)) { return []; }
    return problem.errors.map(function (error) {
      var message = error && typeof error.message === "string" ? error.message : "입력값이 올바르지 않습니다.";
      return error && typeof error.field === "string" && fieldByName(error.field)
        ? labelOf(error.field) + ": " + message
        : message;
    });
  }

  function submit(fields) {
    if (submitting) { return; }
    var checked = validate(fields);
    if (checked.errors.length) {
      showResult(false, "입력 내용을 확인해 주세요.", checked.errors);
      replyToFrame(false);
      return;
    }
    if (!consentBox.checked) {
      showResult(false, "개인정보 수집·이용에 동의해야 신청할 수 있습니다.");
      consentBox.focus();
      replyToFrame(false);
      return;
    }
    submitting = true;
    showResult(true, "신청을 보내는 중입니다.");
    fetch("/api/public/forms/" + encodeURIComponent(slug) + "/submissions", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify({ fields: checked.fields, consent: true, lm: lmParam(), ft: formToken })
    }).then(function (response) {
      if (response.ok) {
        showResult(true, "신청이 접수되었습니다. 감사합니다.");
        consentBox.checked = false;
        replyToFrame(true);
        return;
      }
      return response.json().catch(function () { return null; }).then(function (problem) {
        var detail = problem && typeof problem.detail === "string" ? problem.detail : "신청을 처리하지 못했습니다.";
        showResult(false, detail, problemMessages(problem));
        replyToFrame(false);
        if (problem && problem.code === "invalid_form_token") {
          // 너무 빨리 제출했거나 오래 열어 둔 페이지다. 새 토큰을 받아 두면 사람이 다시 제출할 때 통과한다.
          refreshToken();
        }
      });
    }).catch(function () {
      showResult(false, "네트워크 오류로 신청하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      replyToFrame(false);
    }).then(function () {
      submitting = false;
    });
  }

  window.addEventListener("message", function (event) {
    // 우리 iframe에서 온 메시지만 받는다. sandbox iframe의 출처는 문자열 "null"이다.
    if (!form || event.source !== frame.contentWindow || event.origin !== "null") { return; }
    var data = event.data;
    if (!data || typeof data !== "object") { return; }
    if (data.type === "lm:submit") {
      submit(data.fields);
    } else if (data.type === "lm:resize" && typeof data.height === "number" && isFinite(data.height)) {
      frame.style.height = Math.min(Math.max(Math.ceil(data.height), 0), MAX_FRAME_HEIGHT) + "px";
    }
  });

  function fetchForm() {
    return fetch("/api/public/forms/" + encodeURIComponent(slug), {
      credentials: "same-origin",
      headers: { "Accept": "application/json" }
    });
  }

  function refreshToken() {
    fetchForm().then(function (response) {
      return response.ok ? response.json() : null;
    }).then(function (data) {
      if (data && typeof data.ft === "string") { formToken = data.ft; }
    }).catch(function () {
      // 다음 제출에서 다시 422가 나면 그때 또 받는다.
    });
  }

  function load() {
    if (!slug) {
      setStatus("신청 폼 주소가 올바르지 않습니다.");
      return;
    }
    fetchForm().then(function (response) {
      if (response.status === 410) { throw { userMessage: "마감된 신청입니다." }; }
      if (!response.ok) { throw { userMessage: "신청 폼을 찾을 수 없습니다." }; }
      return response.json();
    }).then(function (data) {
      form = { fields: Array.isArray(data.fields) ? data.fields : [] };
      formToken = typeof data.ft === "string" ? data.ft : null;
      titleEl.textContent = data.title;
      document.title = data.title;
      consentText.textContent = data.consentText;
      frame.srcdoc = withBridge(String(data.html));
      cardEl.hidden = false;
      consentArea.hidden = false;
      setStatus("");
    }).catch(function (error) {
      setStatus(error && error.userMessage ? error.userMessage : "신청 폼을 불러오지 못했습니다.");
    });
  }

  load();
})();
