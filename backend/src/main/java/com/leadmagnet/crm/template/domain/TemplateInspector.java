package com.leadmagnet.crm.template.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 업로드 HTML에서 계약 오류는 아니지만 운영자가 알아야 할 문제(공개 CSP로 막히는 외부 리소스, 라벨·필수 값 누락 등)를
 * 찾는 순수 클래스(ADR 0017). 계약 오류는 {@link FormContractParser}가 맡는다. 어떤 입력에도 예외를 던지지 않고
 * 업로드를 막지 않는다. 업로드, 검사만 하기, 새 버전 업로드가 같은 메서드를 쓴다.
 */
public final class TemplateInspector {

    /** data URI 합계가 이 값(200KB)을 넘으면 large_inline_image. */
    public static final int LARGE_INLINE_BYTES = 200 * 1024;

    public static final int MAX_PER_CODE = 20;
    public static final int MAX_TOTAL = 100;

    static final String EXTERNAL_SCRIPT = "external_script";
    static final String EXTERNAL_STYLESHEET = "external_stylesheet";
    static final String EXTERNAL_IMAGE = "external_image";
    static final String EXTERNAL_FONT = "external_font";
    static final String FORM_ACTION_EXTERNAL = "form_action_external";
    static final String FETCH_IN_SCRIPT = "fetch_in_script";
    static final String MISSING_LABEL = "missing_label";
    static final String CONTACT_NOT_REQUIRED = "contact_not_required";
    static final String CONSENT_IN_HTML = "consent_in_html";
    static final String LARGE_INLINE_IMAGE = "large_inline_image";

    private static final int MAX_LOCATION = 120;
    private static final Set<String> OPTION_TYPES = Set.of("select", "radio", "checkbox");
    private static final Set<String> NON_FIELD_TYPES = Set.of("submit", "button", "reset", "image", "hidden");

    private static final Pattern SRCSET_SPLIT = Pattern.compile(",\\s+|(?<=[wx]),");
    private static final List<String> FONT_EXTENSIONS = List.of(".woff2", ".woff", ".ttf", ".otf", ".eot");
    /** 문서 하나에서 CSS·인라인 스크립트로 훑는 글자 수 상한. 거대한 입력에도 검사 시간을 묶는다. */
    private static final int MAX_SCAN_CHARS = 512 * 1024;
    private static final int MAX_TOKEN = 2048;
    private static final Set<String> JS_TYPES = Set.of("", "text/javascript", "application/javascript", "module");

    private static final Map<String, Pattern> NETWORK_TOKENS = tokens();
    private static final Pattern CONSENT_NAME =
        Pattern.compile("agree|consent|privacy", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSENT_LABEL = Pattern.compile("동의|개인정보|약관");

    private TemplateInspector() {
    }

    private static Map<String, Pattern> tokens() {
        Map<String, Pattern> map = new LinkedHashMap<>();
        map.put("fetch(", Pattern.compile("\\bfetch\\s*\\("));
        map.put("XMLHttpRequest", Pattern.compile("\\bXMLHttpRequest\\b"));
        map.put("sendBeacon", Pattern.compile("\\bsendBeacon\\b"));
        map.put("WebSocket", Pattern.compile("\\bWebSocket\\b"));
        map.put("EventSource", Pattern.compile("\\bEventSource\\b"));
        return map;
    }

    public static List<InspectionWarning> inspect(String html) {
        Collector out = new Collector();
        try {
            Document document = Jsoup.parse(html);
            inspectResources(document, out);
            inspectFormAction(document, out);
            inspectScripts(document, out);
            inspectFields(document, out);
            out.finishInlineSize();
        } catch (RuntimeException e) {
            // 검사는 참고용이다. 예기치 못한 입력으로 업로드를 막지 않는다.
            return List.copyOf(out.warnings());
        }
        return List.copyOf(out.warnings());
    }

    // ---------- 외부 리소스 ----------

    private static void inspectResources(Document document, Collector out) {
        for (Element script : document.select("script[src]")) {
            external(out, EXTERNAL_SCRIPT, script.attr("src"), "<script src=\"%s\">");
            countInline(out, script.attr("src"));
        }
        for (Element link : document.select("link[href]")) {
            inspectLink(link, out);
        }
        for (Element el : document.select("img[src], source[src], video[src], audio[src], input[type=image][src]")) {
            external(out, EXTERNAL_IMAGE, el.attr("src"), "<" + el.tagName() + " src=\"%s\">");
            countInline(out, el.attr("src"));
        }
        for (Element el : document.select("[srcset]")) {
            for (String candidate : SRCSET_SPLIT.split(el.attr("srcset"))) {
                String url = candidate.trim().split("\\s+")[0];
                external(out, EXTERNAL_IMAGE, url, "<" + el.tagName() + " srcset=\"%s\">");
                countInline(out, url);
            }
        }
        for (Element el : document.select("iframe[src], embed[src]")) {
            external(out, EXTERNAL_IMAGE, el.attr("src"), "<" + el.tagName() + " src=\"%s\">");
        }
        for (Element el : document.select("object[data]")) {
            external(out, EXTERNAL_IMAGE, el.attr("data"), "<object data=\"%s\">");
        }
        for (Element el : document.select("image[href], use[href]")) {
            external(out, EXTERNAL_IMAGE, el.attr("href"), "<" + el.tagName() + " href=\"%s\">");
            countInline(out, el.attr("href"));
        }
        for (Element el : document.getAllElements()) {
            if (el.hasAttr("xlink:href")) {
                external(out, EXTERNAL_IMAGE, el.attr("xlink:href"), "<" + el.tagName() + " xlink:href=\"%s\">");
                countInline(out, el.attr("xlink:href"));
            }
        }
        for (Element el : document.select("[poster]")) {
            external(out, EXTERNAL_IMAGE, el.attr("poster"), "<" + el.tagName() + " poster=\"%s\">");
            countInline(out, el.attr("poster"));
        }
        for (Element style : document.select("style")) {
            inspectCss(style.data(), "<style>", out);
        }
        for (Element el : document.select("[style]")) {
            inspectCss(el.attr("style"), "style=\"\"", out);
        }
    }

    private static void inspectLink(Element link, Collector out) {
        String href = link.attr("href");
        Set<String> rel = new LinkedHashSet<>(List.of(link.attr("rel").toLowerCase(Locale.ROOT).trim().split("\\s+")));
        String as = link.attr("as").trim().toLowerCase(Locale.ROOT);
        String location = "<link rel=\"" + link.attr("rel") + "\" href=\"%s\">";
        if (rel.contains("stylesheet")) {
            external(out, EXTERNAL_STYLESHEET, href, location);
        } else if (rel.contains("preload") || rel.contains("prefetch") || rel.contains("modulepreload")) {
            String code = switch (as) {
                case "font" -> EXTERNAL_FONT;
                case "style" -> EXTERNAL_STYLESHEET;
                case "script" -> EXTERNAL_SCRIPT;
                default -> rel.contains("modulepreload") ? EXTERNAL_SCRIPT : EXTERNAL_IMAGE;
            };
            external(out, code, href, location);
        } else if (rel.stream().anyMatch(r -> r.equals("icon") || r.endsWith("-icon") || r.equals("mask-icon"))) {
            external(out, EXTERNAL_IMAGE, href, location);
        }
        countInline(out, href);
    }

    /** CSS를 indexOf로만 훑는다(정규식 역추적 없음). 닫는 기호가 없으면 거기서 멈춘다. */
    private static void inspectCss(String rawCss, String where, Collector out) {
        String css = out.takeBudget(rawCss);
        if (css.isEmpty()) {
            return;
        }
        css = stripComments(css);

        // @import: 문장을 떼어 내며 URL을 모은다.
        StringBuilder withoutImports = new StringBuilder(css.length());
        int pos = 0;
        while (true) {
            int at = indexOfIgnoreCase(css, "@import", pos);
            if (at < 0) {
                break;
            }
            withoutImports.append(css, pos, at);
            int p = skipSpaces(css, at + 7);
            if (startsWithIgnoreCase(css, "url(", p)) {
                p = skipSpaces(css, p + 4);
            }
            if (p < css.length() && (css.charAt(p) == '"' || css.charAt(p) == '\'')) {
                p++;
            }
            int end = p;
            while (end < css.length() && end - p < MAX_TOKEN && !isUrlEnd(css.charAt(end))) {
                end++;
            }
            external(out, EXTERNAL_STYLESHEET, css.substring(p, end), where + " @import %s");
            int semi = css.indexOf(';', end);
            pos = semi < 0 ? end : semi + 1;
        }
        withoutImports.append(css, Math.min(pos, css.length()), css.length());
        css = withoutImports.toString();

        // @font-face { ... } 블록 안의 url()은 폰트다.
        StringBuilder rest = new StringBuilder(css.length());
        pos = 0;
        while (true) {
            int at = indexOfIgnoreCase(css, "@font-face", pos);
            if (at < 0) {
                break;
            }
            int open = skipSpaces(css, at + 10);
            if (open >= css.length() || css.charAt(open) != '{') {
                rest.append(css, pos, at + 10);
                pos = at + 10;
                continue;
            }
            int close = css.indexOf('}', open);
            if (close < 0) {
                break;
            }
            rest.append(css, pos, at);
            scanUrls(css.substring(open, close), EXTERNAL_FONT, where + " @font-face %s", out, false);
            pos = close + 1;
        }
        rest.append(css, Math.min(pos, css.length()), css.length());
        scanUrls(rest.toString(), EXTERNAL_IMAGE, where + " url(%s)", out, true);
    }

    private static void scanUrls(String css, String defaultCode, String location, Collector out, boolean sniffFont) {
        int pos = 0;
        while (true) {
            int at = indexOfIgnoreCase(css, "url(", pos);
            if (at < 0) {
                return;
            }
            int close = css.indexOf(')', at + 4);
            if (close < 0) {
                return;
            }
            String url = css.substring(at + 4, close).trim();
            if (url.length() >= 2 && (url.charAt(0) == '"' || url.charAt(0) == '\'')
                    && url.charAt(url.length() - 1) == url.charAt(0)) {
                url = url.substring(1, url.length() - 1).trim();
            }
            String code = sniffFont && isFontFile(url) ? EXTERNAL_FONT : defaultCode;
            external(out, code, url, location);
            countInline(out, url);
            pos = close + 1;
        }
    }

    private static String stripComments(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int pos = 0;
        while (true) {
            int start = css.indexOf("/*", pos);
            if (start < 0) {
                break;
            }
            sb.append(css, pos, start).append(' ');
            int end = css.indexOf("*/", start + 2);
            if (end < 0) {
                return sb.toString();
            }
            pos = end + 2;
        }
        return sb.append(css, pos, css.length()).toString();
    }

    private static boolean isFontFile(String url) {
        int cut = url.length();
        for (char c : new char[] {'?', '#'}) {
            int i = url.indexOf(c);
            if (i >= 0) {
                cut = Math.min(cut, i);
            }
        }
        String path = url.substring(0, cut).toLowerCase(Locale.ROOT);
        return FONT_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private static boolean isUrlEnd(char c) {
        return c == '"' || c == '\'' || c == ')' || c == ';' || Character.isWhitespace(c);
    }

    private static int skipSpaces(String s, int from) {
        int p = from;
        while (p < s.length() && Character.isWhitespace(s.charAt(p))) {
            p++;
        }
        return p;
    }

    private static boolean startsWithIgnoreCase(String s, String prefix, int at) {
        return s.regionMatches(true, at, prefix, 0, prefix.length());
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int last = s.length() - needle.length();
        for (int i = Math.max(from, 0); i <= last; i++) {
            if (s.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    // ---------- 폼·스크립트 ----------

    private static void inspectFormAction(Document document, Collector out) {
        for (Element form : document.select("form[action]")) {
            String action = form.attr("action");
            if (isExternal(action)) {
                out.add(FORM_ACTION_EXTERNAL, Severity.INFO,
                    "form action이 외부 주소입니다. 공개 폼에서는 action을 쓰지 않고 래퍼가 제출을 받으므로 무시됩니다.",
                    truncate("<form action=\"" + action.trim() + "\">"));
            }
        }
    }

    private static void inspectScripts(Document document, Collector out) {
        StringBuilder inline = new StringBuilder();
        for (Element script : document.select("script:not([src])")) {
            if (!JS_TYPES.contains(script.attr("type").trim().toLowerCase(Locale.ROOT)) || inline.length() >= MAX_SCAN_CHARS) {
                continue;
            }
            inline.append(script.data()).append('\n');
        }
        String code = inline.length() > MAX_SCAN_CHARS ? inline.substring(0, MAX_SCAN_CHARS) : inline.toString();
        NETWORK_TOKENS.forEach((token, pattern) -> {
            if (pattern.matcher(code).find()) {
                out.add(FETCH_IN_SCRIPT, Severity.INFO,
                    "스크립트가 네트워크 호출을 합니다. 공개 폼은 CSP(connect-src 'self') 때문에 외부로는 나가지 못하고, "
                        + "제출은 래퍼가 대신 보냅니다. 이 호출에 기대는 동작은 제한됩니다.",
                    token);
            }
        });
    }

    // ---------- 필드 ----------

    private static void inspectFields(Document document, Collector out) {
        Element form = document.selectFirst("form");
        if (form == null) {
            return;
        }
        Map<String, List<Element>> groups = new LinkedHashMap<>();
        for (Element control : form.select("input, select, textarea")) {
            String type = control.tagName().equals("input") ? control.attr("type").trim().toLowerCase(Locale.ROOT)
                : control.tagName();
            if (!control.hasAttr("name") || NON_FIELD_TYPES.contains(type)) {
                continue;
            }
            groups.computeIfAbsent(control.attr("name"), k -> new ArrayList<>()).add(control);
        }

        boolean contactRequired = false;
        boolean contactPresent = false;
        List<String> contactNames = new ArrayList<>();
        for (Map.Entry<String, List<Element>> entry : groups.entrySet()) {
            String name = entry.getKey();
            List<Element> controls = entry.getValue();
            Element first = controls.get(0);
            String firstType = typeOf(first);
            if (!hasLabel(first, document)) {
                out.add(MISSING_LABEL, Severity.WARNING,
                    "라벨이 없는 입력입니다. <label for=\"id\"> 또는 감싸는 <label>을 붙이면 화면 읽기 도구와 명단 열 이름이 정확해집니다.",
                    name);
            }
            if ((name.equals("phone") || name.equals("email")) && !OPTION_TYPES.contains(firstType)) {
                contactPresent = true;
                contactNames.add(name);
                contactRequired |= controls.stream().anyMatch(c -> c.hasAttr("required"));
            }
            if (firstType.equals("checkbox") && looksLikeConsent(name, first, document)) {
                out.add(CONSENT_IN_HTML, Severity.INFO,
                    "동의 체크박스로 보이는 입력입니다. 개인정보 수집 동의는 폼을 만들 때 입력하는 동의 문구로 래퍼가 따로 받습니다. "
                        + "이 입력은 일반 선택 값으로 저장될 뿐 동의로 처리되지 않습니다.",
                    name);
            }
        }
        if (contactPresent && !contactRequired) {
            out.add(CONTACT_NOT_REQUIRED, Severity.WARNING,
                "phone·email 중 필수(required)인 입력이 없습니다. 연락처 없이 제출되면 신청이 거절됩니다.",
                String.join(", ", contactNames));
        }
    }

    private static String typeOf(Element control) {
        return control.tagName().equals("input") ? control.attr("type").trim().toLowerCase(Locale.ROOT) : control.tagName();
    }

    private static boolean hasLabel(Element control, Document document) {
        if (!control.attr("aria-label").isBlank()) {
            return true;
        }
        String labelledBy = control.attr("aria-labelledby").trim();
        if (!labelledBy.isEmpty()) {
            for (String id : labelledBy.split("\\s+")) {
                Element target = document.getElementById(id);
                if (target != null && !target.text().isBlank()) {
                    return true;
                }
            }
        }
        String id = control.id();
        if (!id.isEmpty()) {
            for (Element label : document.getElementsByAttributeValue("for", id)) {
                if (label.tagName().equals("label") && !label.text().isBlank()) {
                    return true;
                }
            }
        }
        Element wrapping = control.closest("label");
        return wrapping != null && !wrapping.text().isBlank();
    }

    private static boolean looksLikeConsent(String name, Element control, Document document) {
        if (CONSENT_NAME.matcher(name).find()) {
            return true;
        }
        Element label = control.closest("label");
        if (label == null && !control.id().isEmpty()) {
            for (Element candidate : document.getElementsByAttributeValue("for", control.id())) {
                if (candidate.tagName().equals("label")) {
                    label = candidate;
                    break;
                }
            }
        }
        return label != null && CONSENT_LABEL.matcher(label.text()).find();
    }

    // ---------- 공통 ----------

    /** 외부 URL이면 경고를 더한다. location에는 %s 자리에 URL을 끼운 문자열을 쓴다. */
    private static void external(Collector out, String code, String rawUrl, String locationFormat) {
        if (!isExternal(rawUrl)) {
            return;
        }
        String url = rawUrl.replaceAll("[\\t\\r\\n]", "").trim();
        String message = switch (code) {
            case EXTERNAL_SCRIPT -> "외부 스크립트는 공개 폼에서 차단되어 실행되지 않습니다. 필요한 코드는 <script> 안에 직접 넣어 주세요.";
            case EXTERNAL_STYLESHEET -> "외부 CSS는 공개 폼에서 차단되어 스타일이 적용되지 않습니다. CSS를 <style> 안에 직접 넣어 주세요.";
            case EXTERNAL_FONT -> "외부 웹폰트는 공개 폼에서 차단되어 시스템 폰트로 보입니다. 시스템 폰트를 쓰거나 data: URI로 넣어 주세요.";
            default -> "외부 이미지는 공개 폼에서 차단되어 표시되지 않습니다. 이미지는 data: URI로 넣어 주세요.";
        };
        out.add(code, Severity.WARNING, message, truncate(locationFormat.replace("%s", url)));
    }

    private static boolean isExternal(String rawUrl) {
        if (rawUrl == null) {
            return false;
        }
        // 브라우저는 URL 앞의 제어 문자·공백을 떼고 안의 탭·개행을 무시한다.
        StringBuilder sb = new StringBuilder(Math.min(rawUrl.length(), 16));
        for (int i = 0; i < rawUrl.length() && sb.length() < 16; i++) {
            char c = rawUrl.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n' || (sb.isEmpty() && c <= ' ')) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        String url = sb.toString();
        return url.startsWith("http:") || url.startsWith("https:") || url.startsWith("//");
    }

    private static void countInline(Collector out, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.regionMatches(true, 0, "data:", 0, 5)) {
            out.inlineBytes += url.length();
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_LOCATION ? text : text.substring(0, MAX_LOCATION - 1) + "…";
    }

    /** 같은 (code, location)은 한 번만 담고, 코드당 MAX_PER_CODE건·전체 MAX_TOTAL건 안으로 줄여 "외 N건"으로 요약한다. */
    private static final class Collector {
        private static final int MAX_REGULAR = MAX_TOTAL - 10;

        private final Set<String> seen = new LinkedHashSet<>();
        private final List<InspectionWarning> kept = new ArrayList<>();
        private final Map<String, Integer> totals = new LinkedHashMap<>();
        private final Map<String, Integer> keptPerCode = new LinkedHashMap<>();
        private final Map<String, Severity> severities = new LinkedHashMap<>();
        long inlineBytes;
        private int cssBudget = MAX_SCAN_CHARS;

        /** 문서 전체 CSS 검사량을 MAX_SCAN_CHARS로 묶는다. */
        String takeBudget(String css) {
            String taken = css.length() <= cssBudget ? css : css.substring(0, cssBudget);
            cssBudget -= taken.length();
            return taken;
        }

        void add(String code, Severity severity, String message, String location) {
            if (!seen.add(code + '\u0000' + location)) {
                return;
            }
            totals.merge(code, 1, Integer::sum);
            severities.putIfAbsent(code, severity);
            int perCode = keptPerCode.getOrDefault(code, 0);
            if (perCode < MAX_PER_CODE && kept.size() < MAX_REGULAR) {
                kept.add(new InspectionWarning(code, severity, message, location));
                keptPerCode.put(code, perCode + 1);
            }
        }

        void finishInlineSize() {
            if (inlineBytes > LARGE_INLINE_BYTES) {
                add(LARGE_INLINE_IMAGE, Severity.WARNING,
                    "data: URI 이미지·폰트가 합계 " + (inlineBytes / 1024) + "KB입니다(200KB 초과). 폼이 느리게 열리고 템플릿 용량 한도(512KB)에 가까워집니다. "
                        + "이미지를 줄이거나 빼 주세요.",
                    "data URI 합계 " + (inlineBytes / 1024) + "KB");
            }
        }

        List<InspectionWarning> warnings() {
            List<InspectionWarning> result = new ArrayList<>(kept);
            totals.forEach((code, total) -> {
                int omitted = total - keptPerCode.getOrDefault(code, 0);
                if (omitted > 0) {
                    result.add(new InspectionWarning(code, severities.get(code), "외 " + omitted + "건", ""));
                }
            });
            return result;
        }
    }
}
