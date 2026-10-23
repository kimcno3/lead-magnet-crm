package com.leadmagnet.crm.template.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateInspectorTest {

    /** 경고가 하나도 없어야 하는 기준 폼. 각 테스트는 여기에 문제 하나를 끼워 넣는다. */
    private static String page(String head, String body) {
        return """
            <!doctype html><html lang="ko"><head><meta charset="utf-8"><title>t</title>%s</head><body>
            <form>
            <label for="p">휴대폰</label><input id="p" type="tel" name="phone" required>
            %s
            <button type="submit">신청</button></form></body></html>
            """.formatted(head, body);
    }

    private static List<String> codes(String html) {
        return TemplateInspector.inspect(html).stream().map(InspectionWarning::code).toList();
    }

    private static InspectionWarning only(String html, String code) {
        List<InspectionWarning> found = TemplateInspector.inspect(html).stream()
            .filter(w -> w.code().equals(code)).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    @Test
    void 기준_폼은_경고가_없다() {
        assertThat(TemplateInspector.inspect(page("<style>body{margin:0}</style>", ""))).isEmpty();
    }

    @Test
    void 외부_script_src는_external_script_경고다() {
        String html = page("<script src=\"https://cdn.example.com/a.js\"></script>", "");
        InspectionWarning w = only(html, "external_script");
        assertThat(w.severity()).isEqualTo(Severity.WARNING);
        assertThat(w.location()).contains("https://cdn.example.com/a.js");
    }

    @Test
    void 프로토콜_상대_URL도_외부로_본다() {
        assertThat(codes(page("<script src=\"//cdn.example.com/a.js\"></script>", ""))).contains("external_script");
    }

    @Test
    void 상대_경로와_data_URI는_외부가_아니다() {
        String html = page("<link rel=\"stylesheet\" href=\"style.css\">",
            "<img src=\"data:image/png;base64,AAAA\"><img src=\"logo.png\"><script src=\"app.js\"></script>");
        assertThat(codes(html)).isEmpty();
    }

    @Test
    void 외부_link_stylesheet는_external_stylesheet_경고다() {
        assertThat(codes(page("<link rel=\"stylesheet\" href=\"https://cdn.tailwindcss.com/x.css\">", "")))
            .containsExactly("external_stylesheet");
    }

    @Test
    void import는_external_stylesheet_경고다() {
        assertThat(codes(page("<style>@import url('https://fonts.googleapis.com/css?family=Noto');</style>", "")))
            .containsExactly("external_stylesheet");
        assertThat(codes(page("<style>@import \"//x.example/a.css\";</style>", "")))
            .containsExactly("external_stylesheet");
    }

    @Test
    void 외부_img_src와_srcset과_poster는_external_image_경고다() {
        assertThat(codes(page("", "<img src=\"https://img.example.com/a.png\">"))).containsExactly("external_image");
        assertThat(codes(page("", "<img srcset=\"a.png 1x, https://img.example.com/b.png 2x\">")))
            .containsExactly("external_image");
        assertThat(codes(page("", "<video poster=\"http://img.example.com/p.jpg\"></video>")))
            .containsExactly("external_image");
    }

    @Test
    void data_URI가_들어간_srcset은_외부로_오인하지_않는다() {
        assertThat(codes(page("", "<img srcset=\"data:image/png;base64,AAAA 1x, data:image/png;base64,BBBB 2x\">")))
            .isEmpty();
    }

    @Test
    void CSS_url의_외부_이미지는_style_태그와_style_속성_모두_external_image_경고다() {
        assertThat(codes(page("<style>.a{background:url(https://i.example.com/a.png)}</style>", "")))
            .containsExactly("external_image");
        assertThat(codes(page("", "<div style=\"background-image:url('//i.example.com/a.png')\">x</div>")))
            .containsExactly("external_image");
    }

    @Test
    void font_face의_외부_url과_폰트_파일_link_preload는_external_font_경고다() {
        assertThat(codes(page("<style>@font-face{font-family:X;src:url(https://f.example.com/x.woff2)}</style>", "")))
            .containsExactly("external_font");
        assertThat(codes(page("<link rel=\"preload\" as=\"font\" href=\"https://f.example.com/x.woff2\">", "")))
            .containsExactly("external_font");
    }

    @Test
    void link_icon은_external_image_경고다() {
        assertThat(codes(page("<link rel=\"icon\" href=\"https://i.example.com/f.ico\">", "")))
            .containsExactly("external_image");
    }

    @Test
    void 같은_리소스를_두_번_써도_경고는_한_번이다() {
        String html = page("", "<img src=\"https://i.example.com/a.png\"><img src=\"https://i.example.com/a.png\">");
        assertThat(codes(html)).containsExactly("external_image");
    }

    @Test
    void 외부_form_action은_참고_수준_form_action_external이다() {
        String html = page("", "").replace("<form>", "<form action=\"https://evil.example/x\">");
        InspectionWarning w = only(html, "form_action_external");
        assertThat(w.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void 인라인_스크립트의_네트워크_호출은_참고_수준_fetch_in_script다() {
        String html = page("<script>fetch('/x', {method:'POST'}); navigator.sendBeacon('/b');</script>", "");
        List<InspectionWarning> found = TemplateInspector.inspect(html);
        assertThat(found).extracting(InspectionWarning::code).containsOnly("fetch_in_script");
        assertThat(found).extracting(InspectionWarning::severity).containsOnly(Severity.INFO);
        assertThat(found).extracting(InspectionWarning::location).anyMatch(l -> l.contains("fetch"))
            .anyMatch(l -> l.contains("sendBeacon"));
    }

    @Test
    void XMLHttpRequest_WebSocket_EventSource도_찾는다() {
        for (String token : List.of("new XMLHttpRequest()", "new WebSocket('wss://x')", "new EventSource('/e')")) {
            assertThat(codes(page("<script>var a = " + token + ";</script>", ""))).contains("fetch_in_script");
        }
    }

    @Test
    void 외부_script_src는_fetch_in_script로_보지_않고_fetch라는_단어만_있는_글자도_무시한다() {
        assertThat(codes(page("<script>var prefetch = 1; // refetch later</script>", ""))).isEmpty();
    }

    @Test
    void 라벨_없는_필드는_missing_label_경고다() {
        String html = page("", "<input type=\"text\" name=\"company\">");
        InspectionWarning w = only(html, "missing_label");
        assertThat(w.severity()).isEqualTo(Severity.WARNING);
        assertThat(w.location()).isEqualTo("company");
    }

    @Test
    void for_연결_감싼_label_aria_label이_있으면_라벨_있음이다() {
        String body = """
            <label for="c1">회사</label><input id="c1" name="company">
            <label>직급 <input name="title"></label>
            <input name="memo" aria-label="메모">
            <input type="hidden" name="utm">
            """;
        assertThat(codes(page("", body))).isEmpty();
    }

    @Test
    void phone이나_email_중_필수가_하나도_없으면_contact_not_required_경고다() {
        String html = page("", "").replace(" required>", ">");
        InspectionWarning w = only(html, "contact_not_required");
        assertThat(w.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void 둘_중_하나라도_필수면_contact_not_required가_아니다() {
        String body = "<label for=\"e\">이메일</label><input id=\"e\" type=\"email\" name=\"email\">";
        assertThat(codes(page("", body))).isEmpty();
    }

    @Test
    void 동의_체크박스로_보이면_참고_수준_consent_in_html이다() {
        String body = "<label><input type=\"checkbox\" name=\"privacy_agree\" required> 개인정보 수집에 동의합니다</label>";
        InspectionWarning w = only(page("", body), "consent_in_html");
        assertThat(w.severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void 이름이_달라도_라벨에_동의가_있으면_consent_in_html이다() {
        String body = "<label><input type=\"checkbox\" name=\"ok\"> 약관에 동의</label>";
        assertThat(codes(page("", body))).containsExactly("consent_in_html");
    }

    @Test
    void 일반_체크박스는_consent_in_html이_아니다() {
        String body = "<label><input type=\"checkbox\" name=\"interests\" value=\"a\"> 자동화</label>";
        assertThat(codes(page("", body))).isEmpty();
    }

    @Test
    void data_URI_합계가_200KB를_넘으면_large_inline_image_경고다() {
        String big = "A".repeat(110 * 1024);
        String html = page("", "<img src=\"data:image/png;base64," + big + "\"><img src=\"data:image/png;base64," + big + "\">");
        InspectionWarning w = only(html, "large_inline_image");
        assertThat(w.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void data_URI_하나가_200KB_이하면_경고가_없다() {
        String ok = "A".repeat(100 * 1024);
        assertThat(codes(page("", "<img src=\"data:image/png;base64," + ok + "\">"))).isEmpty();
    }

    @Test
    void 여러_문제가_있으면_모두_모은다() {
        String html = page("<script src=\"https://a.example/x.js\"></script><link rel=\"stylesheet\" href=\"https://a.example/x.css\">",
            "<img src=\"https://a.example/i.png\">");
        assertThat(codes(html)).containsExactlyInAnyOrder("external_script", "external_stylesheet", "external_image");
    }
}
