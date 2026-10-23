package com.leadmagnet.crm.template.domain;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 업로드 HTML이 폼 계약(ADR 0004)을 지키는지 검사하고 필드를 추출하는 순수 클래스. */
public final class FormContractParser {

    public static final int MAX_FIELDS = 50;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,49}$");
    /** value 속성이 없는 radio·checkbox를 선택하면 브라우저가 보내는 값. */
    static final String DEFAULT_CHOICE_VALUE = "on";
    private static final Set<String> OPTION_TYPES = Set.of("select", "radio", "checkbox");
    private static final Set<String> NON_FIELD_INPUT_TYPES = Set.of("submit", "button", "reset", "image");
    private static final Set<String> FORBIDDEN_INPUT_TYPES = Set.of("file", "password");
    private static final Set<String> KNOWN_TYPES =
        Set.of("text", "email", "tel", "number", "date", "textarea", "select", "radio", "checkbox", "hidden", "url");

    private FormContractParser() {
    }

    public static List<FormField> parse(String html) {
        Document document = Jsoup.parse(html);
        List<Element> forms = document.select("form");
        if (forms.size() != 1) {
            throw new DomainException(ErrorCode.TEMPLATE_FORM_COUNT_INVALID,
                "form이 정확히 하나 있어야 합니다(현재 " + forms.size() + "개)");
        }
        Element form = forms.get(0);

        Map<String, Group> groups = new LinkedHashMap<>();
        for (Element control : form.select("input, select, textarea")) {
            String type = controlType(control);
            if (FORBIDDEN_INPUT_TYPES.contains(type)) {
                throw new DomainException(ErrorCode.TEMPLATE_FORBIDDEN_FIELD, type + " 입력은 사용할 수 없습니다");
            }
            if (!control.hasAttr("name") || NON_FIELD_INPUT_TYPES.contains(type)) {
                continue;
            }
            String name = control.attr("name");
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new DomainException(ErrorCode.TEMPLATE_FIELD_INVALID,
                    "필드 이름은 영문자로 시작하는 50자 이하 영문·숫자·밑줄이어야 합니다: " + name);
            }
            Group group = groups.get(name);
            if (group == null) {
                groups.put(name, new Group(control, normalizeType(control, type)));
            } else {
                group.add(control, normalizeType(control, type));
            }
        }

        if (groups.size() > MAX_FIELDS) {
            throw new DomainException(ErrorCode.TEMPLATE_FIELD_INVALID, "필드는 최대 " + MAX_FIELDS + "개까지 쓸 수 있습니다");
        }
        // 선택지 타입의 phone·email은 연락처 값이 아니어서(SubmissionValidator가 payload로 보낸다) 연락처로 치지 않는다.
        if (!isContactField(groups.get("phone")) && !isContactField(groups.get("email"))) {
            throw new DomainException(ErrorCode.TEMPLATE_CONTACT_FIELD_MISSING);
        }

        List<FormField> fields = new ArrayList<>();
        groups.forEach((name, group) -> fields.add(group.toField(name, document)));
        return List.copyOf(fields);
    }

    private static boolean isContactField(Group group) {
        return group != null && !OPTION_TYPES.contains(group.type);
    }

    private static String controlType(Element control) {
        return switch (control.tagName()) {
            case "select" -> "select";
            case "textarea" -> "textarea";
            default -> control.attr("type").trim().toLowerCase(Locale.ROOT);
        };
    }

    private static String normalizeType(Element control, String type) {
        if (control.tagName().equals("input") && type.isEmpty()) {
            return "text";
        }
        return KNOWN_TYPES.contains(type) ? type : "text";
    }

    private static String labelFor(Element control, Document document) {
        String id = control.id();
        if (!id.isEmpty()) {
            for (Element label : document.getElementsByAttributeValue("for", id)) {
                if (label.tagName().equals("label") && !label.text().isBlank()) {
                    return label.text().trim();
                }
            }
        }
        Element wrapping = control.closest("label");
        if (wrapping != null && !wrapping.text().isBlank()) {
            return wrapping.text().trim();
        }
        return null;
    }

    private static final class Group {
        private final Element first;
        private final String type;
        private boolean required;
        private final List<String> options = new ArrayList<>();

        Group(Element first, String type) {
            this.first = first;
            this.type = type;
            collect(first);
        }

        void add(Element control, String controlType) {
            boolean sameChoiceType = controlType.equals(type) && (type.equals("radio") || type.equals("checkbox"));
            if (!sameChoiceType) {
                throw new DomainException(ErrorCode.TEMPLATE_FIELD_INVALID,
                    "같은 이름은 radio 또는 checkbox끼리만 쓸 수 있습니다: " + control.attr("name"));
            }
            collect(control);
        }

        private void collect(Element control) {
            required |= control.hasAttr("required");
            switch (control.tagName()) {
                case "select" -> control.select("option").forEach(option ->
                    options.add(option.hasAttr("value") ? option.attr("value") : option.text().trim()));
                case "input" -> {
                    if (type.equals("radio") || type.equals("checkbox")) {
                        if (control.hasAttr("value")) {
                            options.add(control.attr("value"));
                        } else if (!options.contains(DEFAULT_CHOICE_VALUE)) {
                            // value가 없으면 브라우저는 "on"을 보낸다(HTML 표준 기본값).
                            options.add(DEFAULT_CHOICE_VALUE);
                        }
                    }
                }
                default -> { }
            }
        }

        FormField toField(String name, Document document) {
            String label = labelFor(first, document);
            if (label == null) {
                String placeholder = first.attr("placeholder").trim();
                label = placeholder.isEmpty() ? name : placeholder;
            }
            return new FormField(name, type, required, label, List.copyOf(options));
        }
    }
}
