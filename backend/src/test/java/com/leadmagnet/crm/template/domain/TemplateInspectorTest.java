package com.leadmagnet.crm.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    // ---------- 성능·상한 ----------

    private static void within2s(String html) {
        within(Duration.ofSeconds(2), html);
    }

    private static void within(Duration limit, String html) {
        assertTimeoutPreemptively(limit, () -> TemplateInspector.inspect(html));
    }

    @Test
    void 닫히지_않는_url_여러_개도_선형_시간에_끝난다() {
        within2s(page("<style>" + "url(".repeat(120_000) + "</style>", ""));
        within2s(page("<style>" + "url(".repeat(16_000) + "</style>", ""));
    }

    @Test
    void 닫히지_않는_font_face_여러_개도_선형_시간에_끝난다() {
        within2s(page("<style>" + "@font-face{".repeat(40_000) + "</style>", ""));
    }

    @Test
    void 닫히지_않는_주석_import_srcset도_선형_시간에_끝난다() {
        within2s(page("<style>" + "/*".repeat(200_000) + "</style>", ""));
        // 선형이면 0.7초 안팎이다. 느린 CI에서 흔들리지 않게 한도만 넓힌다(제곱 시간이면 수십 초라 여전히 잡는다).
        within(Duration.ofSeconds(5), page("<style>" + "@import ".repeat(60_000) + "</style>", ""));
        within2s(page("", "<img srcset=\"" + "a ,".repeat(100_000) + "\">"));
        within2s(page("<script>" + "fetch ".repeat(80_000) + "</script>", ""));
        within2s(page("<style>@font-face{src:url(data:font/woff;base64," + ".woff".repeat(80_000) + ")}</style>", ""));
    }

    @Test
    void 한_코드의_경고는_20건까지만_담고_나머지는_외_N건_한_줄로_요약한다() {
        String imgs = IntStream.range(0, 50).mapToObj(i -> "<img src=\"https://i.example/" + i + ".png\">")
            .collect(Collectors.joining());
        List<InspectionWarning> found = TemplateInspector.inspect(page("", imgs));

        List<InspectionWarning> images = found.stream().filter(w -> w.code().equals("external_image")).toList();
        assertThat(images).hasSize(21);
        InspectionWarning summary = images.get(images.size() - 1);
        assertThat(summary.message()).isEqualTo("외 30건");
        assertThat(summary.severity()).isEqualTo(Severity.WARNING);
        assertThat(images.subList(0, 20)).extracting(InspectionWarning::location).allMatch(l -> l.contains("https://i.example/"));
    }

    @Test
    void 전체_경고는_100건을_넘지_않는다() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            body.append("<img src=\"https://i.example/").append(i).append(".png\">")
                .append("<script src=\"https://s.example/").append(i).append(".js\"></script>")
                .append("<video poster=\"https://p.example/").append(i).append(".png\"></video>")
                .append("<input name=\"f").append(i).append("\">");
        }
        body.append("<link rel=\"stylesheet\" href=\"https://c.example/a.css\">");
        List<InspectionWarning> found = TemplateInspector.inspect(page("", body.toString()));

        assertThat(found.size()).isLessThanOrEqualTo(TemplateInspector.MAX_TOTAL);
        assertThat(found).extracting(InspectionWarning::message).anyMatch(m -> m.startsWith("외 "));
    }

    @Test
    void 이상한_입력에도_예외를_던지지_않는다() {
        List<String> inputs = List.of("", "<", "<<<>>>", "<form", "<style>url(", "<style>@import", "<img srcset=\",\">",
            "<svg><use xlink:href=\"//x\"/></svg>", "\u0000\u0000", "<script>fetch(", "<form><input name></form>",
            "<style>@font-face{", "<link rel>", "<img src=\"http", page("<style>url(\"", ""));
        for (String input : inputs) {
            assertThat(TemplateInspector.inspect(input)).isNotNull();
        }
    }

    // ---------- 보강 ----------

    @Test
    void 스킴_안의_탭_개행과_앞_공백으로_가려도_외부로_본다() {
        assertThat(codes(page("<script src=\"ht\ttps://x.example/a.js\"></script>", ""))).contains("external_script");
        assertThat(codes(page("<script src=\"  \n https://x.example/a.js\"></script>", ""))).contains("external_script");
        assertThat(codes(page("", "<img src=\"java\nscript:x\">"))).isEmpty();
    }

    @Test
    void iframe_object_embed_svg_image_use와_xlink_href의_외부_URL도_찾는다() {
        assertThat(codes(page("", "<iframe src=\"https://x.example/\"></iframe>"))).containsExactly("external_image");
        assertThat(codes(page("", "<object data=\"https://x.example/a.svg\"></object>"))).containsExactly("external_image");
        assertThat(codes(page("", "<embed src=\"//x.example/a.swf\">"))).containsExactly("external_image");
        assertThat(codes(page("", "<svg><image href=\"https://x.example/a.png\"/></svg>"))).containsExactly("external_image");
        assertThat(codes(page("", "<svg><use href=\"https://x.example/s.svg#a\"/></svg>"))).containsExactly("external_image");
        assertThat(codes(page("", "<svg><use xlink:href=\"https://x.example/s.svg#a\"/></svg>"))).containsExactly("external_image");
        assertThat(codes(page("", "<svg><use href=\"#a\"/></svg>"))).isEmpty();
    }

    @Test
    void 이름에_policy_terms가_들어간_일반_체크박스는_동의로_보지_않는다() {
        String body = "<label><input type=\"checkbox\" name=\"insurance_policy\" value=\"a\"> 보험 가입</label>"
            + "<label><input type=\"checkbox\" name=\"terms_of_trade\" value=\"b\"> 거래 조건</label>";
        assertThat(codes(page("", body))).isEmpty();
    }

    @Test
    void 자바스크립트가_아닌_script_type은_fetch_검사에서_뺀다() {
        for (String type : List.of("application/json", "application/ld+json", "text/template", "text/x-handlebars")) {
            assertThat(codes(page("<script type=\"" + type + "\">{\"a\": \"fetch(\"}</script>", ""))).isEmpty();
        }
        assertThat(codes(page("<script type=\"module\">fetch('/x')</script>", ""))).contains("fetch_in_script");
        assertThat(codes(page("<script type=\"text/javascript\">fetch('/x')</script>", ""))).contains("fetch_in_script");
    }

    private static String labeled(String type, String name) {
        return "<label for=\"x\">값</label><input id=\"x\" type=\"" + type + "\" name=\"" + name + "\">";
    }

    @Test
    void phone_email_밖의_tel_email_타입_입력은_extra_contact_field_경고다() {
        InspectionWarning w = only(page("", labeled("tel", "alt_contact")), "extra_contact_field");
        assertThat(w.severity()).isEqualTo(Severity.WARNING);
        assertThat(w.location()).isEqualTo("alt_contact");
        assertThat(w.message()).contains("마스킹").contains("payload");
        assertThat(codes(page("", labeled("email", "backup")))).contains("extra_contact_field");
    }

    @Test
    void 연락처를_뜻하는_이름의_텍스트_계열_입력은_경고다() {
        for (String name : List.of("mobile", "home_tel", "hp", "cell-number", "work_mail", "contactEmail")) {
            assertThat(codes(page("", labeled("text", name)))).as(name).contains("extra_contact_field");
        }
        assertThat(codes(page("", labeled("number", "phone2")))).contains("extra_contact_field");
    }

    @Test
    void 예약_이름_phone_email과_무관한_입력은_경고가_없다() {
        String email = "<label for=\"e\">이메일</label><input id=\"e\" type=\"email\" name=\"email\">";
        assertThat(codes(page("", email))).doesNotContain("extra_contact_field");
        assertThat(codes(page("", labeled("text", "hotel")))).doesNotContain("extra_contact_field");
        assertThat(codes(page("", labeled("text", "company")))).doesNotContain("extra_contact_field");
        assertThat(codes(page("", labeled("checkbox", "email_opt_in")))).doesNotContain("extra_contact_field");
        assertThat(codes(page("", "<label for=\"x\">m</label><select id=\"x\" name=\"mobile_pref\"><option>a</option></select>")))
            .doesNotContain("extra_contact_field");
    }

    @Test
    void 같은_이름의_추가_연락처는_한_번만_경고한다() {
        String twice = labeled("tel", "alt") + labeled("tel", "alt");
        assertThat(codes(page("", twice)).stream().filter("extra_contact_field"::equals).count()).isEqualTo(1);
    }
}
