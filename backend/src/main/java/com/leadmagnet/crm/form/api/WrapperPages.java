package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 공개 폼 페이지의 고정 HTML. 서버가 어떤 값도 끼워 넣지 않는다(템플릿 엔진 없음). 슬러그는 스크립트가
 * location.pathname에서 읽고, 제목·동의 문구·업로드 HTML은 textContent와 iframe srcdoc으로만 들어간다 (ADR 0002).
 * 업로드 HTML은 sandbox="allow-scripts allow-forms" iframe에서만 돈다. allow-same-origin은 금지다.
 * 404·410 안내 페이지는 브라우저가 여는 공개 경로(/f/{slug}, /l/{code})가 함께 쓴다.
 */
public final class WrapperPages {

    static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private static final String STYLE = """
        <style>
          :root { color-scheme: light; --fg: #1d1d1f; --muted: #5f6368; --line: #dadce0; --accent: #1a56db;
                  --ok: #146c2e; --error: #b3261e; --bg: #f6f7f9; }
          * { box-sizing: border-box; }
          body { margin: 0; background: var(--bg); color: var(--fg);
                 font: 16px/1.6 system-ui, -apple-system, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif; }
          main { max-width: 680px; margin: 0 auto; padding: 32px 16px 48px; }
          h1 { font-size: 1.5rem; line-height: 1.3; margin: 0 0 16px; }
          .card { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
          iframe { display: block; width: 100%; min-height: 320px; border: 0; }
          .consent { display: flex; gap: 10px; align-items: flex-start; margin: 16px 0 0; padding: 14px 16px;
                     background: #fff; border: 1px solid var(--line); border-radius: 12px; }
          .consent input { width: 20px; height: 20px; margin: 2px 0 0; flex: none; accent-color: var(--accent); }
          .consent label { white-space: pre-line; }
          .status { color: var(--muted); margin: 0 0 16px; }
          .status:empty { display: none; }
          .result { margin: 16px 0 0; }
          .result:empty { display: none; }
          .result p { margin: 0; font-weight: 600; }
          .result ul { margin: 8px 0 0; padding-left: 20px; }
          .result.ok { color: var(--ok); }
          .result.error { color: var(--error); }
          [hidden] { display: none !important; }
        </style>
        """;

    static final String SHELL = """
        <!doctype html>
        <html lang="ko">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>신청 폼</title>
        """ + STYLE + """
        <script src="/f/_assets/form-wrapper.js" defer></script>
        </head>
        <body>
        <main>
          <h1 id="lm-title">신청 폼</h1>
          <p id="lm-status" class="status" role="status" aria-live="polite">신청 폼을 불러오는 중입니다.</p>
          <div class="card" id="lm-form-card" hidden>
            <iframe id="lm-frame" sandbox="allow-scripts allow-forms" title="신청 폼"></iframe>
          </div>
          <div class="consent" id="lm-consent-area" hidden>
            <input type="checkbox" id="lm-consent" name="lm-consent">
            <label for="lm-consent" id="lm-consent-text"></label>
          </div>
          <div id="lm-result" class="result" role="status" aria-live="polite"></div>
        </main>
        </body>
        </html>
        """;

    static final String NOT_FOUND = notice("신청 폼을 찾을 수 없습니다", "주소가 올바른지 확인해 주세요.");

    static final String GONE = notice("마감된 신청입니다", "신청 기간이 끝났습니다. 관심 가져 주셔서 감사합니다.");

    private WrapperPages() {
    }

    /** 브라우저 페이지용 404·410 HTML 안내. GONE이면 마감 안내, 그 밖에는 찾을 수 없음 안내이고 상태는 code를 따른다. */
    public static ResponseEntity<String> notice(ErrorCode code) {
        String body = code == ErrorCode.GONE ? GONE : NOT_FOUND;
        return ResponseEntity.status(code.status()).contentType(HTML).body(body);
    }

    private static String notice(String heading, String message) {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            %s</head>
            <body>
            <main>
              <h1>%s</h1>
              <p class="status">%s</p>
            </main>
            </body>
            </html>
            """.formatted(heading, STYLE, heading, message);
    }
}
