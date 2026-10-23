package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.PiiMasker;
import com.leadmagnet.crm.template.domain.ContactLikeField;
import com.leadmagnet.crm.template.domain.ContactLikeField.Kind;
import com.leadmagnet.crm.template.domain.FormField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * payload 안의 연락처처럼 보이는 추가 항목(phone·email 이름이 아닌 tel·email 타입, 연락처 이름 토큰)을 가리거나 골라낸다 (ADR 0015).
 * 판단은 폼의 fieldConfig 타입과 {@link ContactLikeField}의 이름 규칙이다. 폼이 사라졌거나 fieldConfig에 없는 키는 이름만으로 판단한다.
 */
final class ExtraContacts {

    private static final String UNKNOWN_SHAPE = "****";

    private ExtraContacts() {
    }

    /** 연락처처럼 보이는 값을 이름·전화번호·이메일과 같은 규칙으로 가린 payload. 나머지 값은 그대로다. */
    static Map<String, Object> mask(Map<String, Object> payload, List<FormField> config) {
        if (payload == null || payload.isEmpty()) {
            return payload;
        }
        Map<String, String> types = types(config);
        Map<String, Object> result = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            Kind kind = kindOf(key, types);
            result.put(key, kind == null || value == null ? value : maskValue(kind, value));
        });
        return result;
    }

    /** 연락처처럼 보이는 항목의 원문(필드 이름 → 문자열). 없으면 빈 맵. 원문 열람 API 전용이다. */
    static Map<String, String> raw(Map<String, Object> payload, List<FormField> config) {
        Map<String, String> result = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return result;
        }
        Map<String, String> types = types(config);
        payload.forEach((key, value) -> {
            if (value != null && kindOf(key, types) != null) {
                result.put(key, value instanceof List<?> items
                    ? items.stream().map(String::valueOf).collect(Collectors.joining(", ")) : String.valueOf(value));
            }
        });
        return result;
    }

    private static Map<String, String> types(List<FormField> config) {
        return config == null ? Map.of()
            : config.stream().filter(f -> f.name() != null && f.type() != null)
                .collect(Collectors.toMap(FormField::name, FormField::type, (a, b) -> a));
    }

    private static Kind kindOf(String key, Map<String, String> types) {
        return ContactLikeField.extraKindOf(key, types.getOrDefault(key, ""));
    }

    private static Object maskValue(Kind kind, Object value) {
        if (value instanceof List<?> items) {
            return items.stream().map(item -> maskOne(kind, item)).toList();
        }
        return maskOne(kind, value);
    }

    private static String maskOne(Kind kind, Object value) {
        String text = String.valueOf(value);
        String masked = kind == Kind.EMAIL ? PiiMasker.maskEmailForDisplay(text) : PiiMasker.maskPhoneForDisplay(text);
        return masked == null ? UNKNOWN_SHAPE : masked;
    }
}
