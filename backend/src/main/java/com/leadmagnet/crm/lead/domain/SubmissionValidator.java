package com.leadmagnet.crm.lead.domain;

import com.leadmagnet.crm.common.TextInput;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.template.domain.FormContractParser;
import com.leadmagnet.crm.template.domain.FormField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 제출 값을 폼의 fieldConfig로 검증하고 정규화한다. 업로드 HTML이 래퍼를 거치지 않고 API를 직접 부를 수 있으므로
 * 래퍼의 검증과 별개로 서버가 최종 경계다. 오류는 첫 번째에서 멈추지 않고 모두 모아 한 번에 돌려준다.
 *
 * <p>예약 필드 이름 {@code name}·{@code phone}·{@code email}은 컬럼으로 가고 나머지는 payload로 간다.
 * 전화번호·이메일 규칙은 이 이름과 {@code tel}·{@code email} 타입 필드에 적용한다.
 */
public final class SubmissionValidator {

    static final int MAX_VALUE_LENGTH = 1000;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_EMAIL_LENGTH = 254;
    /** 응답이 입력 크기에 비례해 커지지 않도록 알려 주는 unknown_field 오류 수의 상한. */
    static final int MAX_UNKNOWN_FIELD_ERRORS = 10;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern KOREAN_MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");

    private SubmissionValidator() {
    }

    public static Submission validate(List<FormField> config, Map<String, Object> fields, boolean consent) {
        Map<String, Object> input = fields == null ? Map.of() : fields;
        Map<String, FormField> byName = config.stream()
            .collect(Collectors.toMap(FormField::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (input.size() > FormContractParser.MAX_FIELDS) {
            throw DomainException.validation(List.of(new FieldError("fields",
                "입력 항목은 최대 " + FormContractParser.MAX_FIELDS + "개까지 보낼 수 있습니다", "too_many_fields")));
        }
        List<FieldError> errors = new ArrayList<>();
        boolean contactFieldFailed = false;

        input.keySet().stream().filter(name -> !byName.containsKey(name)).limit(MAX_UNKNOWN_FIELD_ERRORS).forEach(name ->
            errors.add(new FieldError(name, "폼에 없는 입력 항목입니다", "unknown_field")));

        String name = null;
        String phone = null;
        String email = null;
        Map<String, Object> payload = new LinkedHashMap<>();

        for (FormField field : config) {
            int before = errors.size();
            Object value = clean(field, input.get(field.name()), errors);
            if (errors.size() == before) {
                if (value == null) {
                    if (field.required()) {
                        errors.add(error(field, "필수 입력 항목입니다", "required"));
                    }
                } else {
                    value = normalize(field, value, errors);
                }
            }
            boolean failed = errors.size() > before;
            if (isContact(field)) {
                contactFieldFailed |= failed;
            }
            if (failed || value == null) {
                continue;
            }
            // 선택지 타입(select·radio·checkbox)인 예약 이름 필드는 연락처 값이 아니므로 payload로 보낸다.
            String column = isOptionType(field.type()) ? "" : field.name();
            switch (column) {
                case "name" -> name = (String) value;
                case "phone" -> phone = (String) value;
                case "email" -> email = (String) value;
                default -> payload.put(field.name(), value);
            }
        }

        if (!consent) {
            errors.add(new FieldError("consent", "개인정보 수집·이용에 동의해야 합니다", "consent_required"));
        }
        if (phone == null && email == null && !contactFieldFailed) {
            errors.add(new FieldError("contact", "전화번호 또는 이메일 중 하나는 입력해야 합니다", "contact_required"));
        }
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }
        return new Submission(name, phone, email, payload);
    }

    /** 한국 휴대전화(010 등) 번호에서 숫자만 남긴다. 형식이 맞지 않으면 null. */
    public static String normalizePhone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        return KOREAN_MOBILE.matcher(digits).matches() ? digits : null;
    }

    /** 앞뒤 공백을 자르고 소문자로 바꾼다. 형식이 맞지 않거나 254자를 넘으면 null. */
    public static String normalizeEmail(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 타입(문자열/배열), 제어 문자(NUL 등, 공백 정리 전 원문 기준), 길이를 확인하고 공백을 정리한다. 비어 있으면 null, 오류가 있으면 errors에 담고 null.
     * 배열은 checkbox에만 허용하며, 빈 항목은 버린다.
     */
    private static Object clean(FormField field, Object raw, List<FieldError> errors) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            if (TextInput.containsForbiddenControl(text)) {
                errors.add(controlCharError(field));
                return null;
            }
            String trimmed = text.trim();
            if (trimmed.length() > MAX_VALUE_LENGTH) {
                errors.add(error(field, "값은 " + MAX_VALUE_LENGTH + "자 이하여야 합니다", "too_long"));
                return null;
            }
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (raw instanceof List<?> items && "checkbox".equals(field.type())) {
            if (items.size() > field.options().size()) {
                errors.add(error(field, "선택지보다 많은 값을 보낼 수 없습니다", "invalid_value"));
                return null;
            }
            List<String> kept = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof String text)) {
                    errors.add(error(field, "문자열 목록이어야 합니다", "invalid_value"));
                    return null;
                }
                if (TextInput.containsForbiddenControl(text)) {
                    errors.add(controlCharError(field));
                    return null;
                }
                String trimmed = text.trim();
                if (trimmed.length() > MAX_VALUE_LENGTH) {
                    errors.add(error(field, "값은 " + MAX_VALUE_LENGTH + "자 이하여야 합니다", "too_long"));
                    return null;
                }
                if (!trimmed.isEmpty()) {
                    kept.add(trimmed);
                }
            }
            if (kept.size() != kept.stream().distinct().count()) {
                errors.add(error(field, "같은 값을 두 번 보낼 수 없습니다", "invalid_value"));
                return null;
            }
            return kept.isEmpty() ? null : List.copyOf(kept);
        }
        errors.add(error(field, "이 항목은 문자열 하나만 받을 수 있습니다", "invalid_value"));
        return null;
    }

    private static Object normalize(FormField field, Object value, List<FieldError> errors) {
        if (isOptionType(field.type())) {
            // checkbox는 값이 하나여도 항상 목록으로 저장한다.
            Object stored = value instanceof String s && "checkbox".equals(field.type()) ? List.of(s) : value;
            List<String> chosen = stored instanceof String s ? List.of(s) : castList(stored);
            if (!field.options().containsAll(chosen)) {
                errors.add(error(field, "선택할 수 없는 값입니다", "invalid_option"));
                return null;
            }
            return stored;
        }
        String text = (String) value;
        if (isPhone(field)) {
            String phone = normalizePhone(text);
            if (phone == null) {
                errors.add(error(field, "휴대전화 번호 형식이 올바르지 않습니다", "invalid_format"));
            }
            return phone;
        }
        if (isEmail(field)) {
            String email = normalizeEmail(text);
            if (email == null) {
                errors.add(error(field, "이메일 형식이 올바르지 않습니다", "invalid_format"));
            }
            return email;
        }
        if ("name".equals(field.name()) && text.length() > MAX_NAME_LENGTH) {
            errors.add(error(field, "이름은 " + MAX_NAME_LENGTH + "자 이하여야 합니다", "too_long"));
            return null;
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    private static boolean isOptionType(String type) {
        return "select".equals(type) || "radio".equals(type) || "checkbox".equals(type);
    }

    private static boolean isPhone(FormField field) {
        return "phone".equals(field.name()) || "tel".equals(field.type());
    }

    private static boolean isEmail(FormField field) {
        return "email".equals(field.name()) || "email".equals(field.type());
    }

    private static boolean isContact(FormField field) {
        return isPhone(field) || isEmail(field);
    }

    private static FieldError controlCharError(FormField field) {
        return error(field, "제어 문자는 입력할 수 없습니다", "invalid_value");
    }

    private static FieldError error(FormField field, String message, String code) {
        return new FieldError(field.name(), message, code);
    }
}
