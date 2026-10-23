package com.leadmagnet.crm.template.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 예약 필드(phone·email)가 아닌데 연락처처럼 보이는 입력을 가려내는 규칙 (ADR 0015, 0017).
 * 이런 값은 암호화 대상이 아닌 payload에 남으므로 업로드 때 경고하고({@code extra_contact_field}) 명단·마스킹 CSV에서 가린다.
 * 입력 타입이 {@code tel}·{@code email}이거나, 텍스트 계열 입력(text·tel·email·number)의 이름이 연락처 토큰을 포함하면 해당한다.
 * 이름은 {@code _}·{@code -}와 camelCase 경계로 토큰을 나눠 비교하므로 {@code hotel}, {@code email_opt_in} 체크박스는 걸리지 않는다.
 */
public final class ContactLikeField {

    private static final Set<String> TEXT_LIKE_TYPES = Set.of("text", "tel", "email", "number");
    private static final Set<String> PHONE_TOKENS = Set.of("phone", "tel", "mobile", "hp", "cell");
    private static final Set<String> EMAIL_TOKENS = Set.of("email", "mail");
    private static final Set<String> KOREAN_PHONE_TOKENS = Set.of("연락처", "전화");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+|(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})");

    /** 어떤 종류의 연락처로 보이는지. */
    public enum Kind { PHONE, EMAIL }

    private ContactLikeField() {
    }

    /** 연락처처럼 보이면 종류를, 아니면 null. type은 {@link FormField#type()} 값이다. */
    public static Kind kindOf(String name, String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.equals("tel")) {
            return Kind.PHONE;
        }
        if (t.equals("email")) {
            return Kind.EMAIL;
        }
        if (!t.isEmpty() && !TEXT_LIKE_TYPES.contains(t) || name == null) {
            return null;
        }
        boolean phone = false;
        for (String token : SPLIT.split(name)) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (EMAIL_TOKENS.contains(lower)) {
                return Kind.EMAIL;
            }
            phone |= PHONE_TOKENS.contains(lower) || KOREAN_PHONE_TOKENS.stream().anyMatch(lower::contains);
        }
        return phone ? Kind.PHONE : null;
    }

    /** 예약 이름(phone·email)은 컬럼으로 가므로 제외한다. */
    public static boolean isExtra(String name, String type) {
        return !"phone".equals(name) && !"email".equals(name) && kindOf(name, type) != null;
    }
}
