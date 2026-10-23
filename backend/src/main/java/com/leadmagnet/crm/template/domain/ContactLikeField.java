package com.leadmagnet.crm.template.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 예약 필드(phone·email)가 아닌데 연락처처럼 보이는 입력을 가려내는 규칙 (ADR 0015, 0017).
 * 이런 값은 암호화 대상이 아닌 payload에 남으므로 업로드 때 경고하고({@code extra_contact_field}) 명단·마스킹 CSV에서 가린다.
 * 입력 타입이 {@code tel}·{@code email}이거나, 텍스트 계열 입력(text·tel·email·number)의 이름이 연락처 토큰을 포함하면 해당한다.
 * 이름은 {@code _}·{@code -}와 camelCase 경계로 토큰을 나눠 비교한다. 토큰이 목록(phone, tel, mobile, hp, cell, email, mail)이나
 * 합성어 허용 목록(telephone, phonenumber, emailaddress 등)과 같거나 phone·email·mobile로 시작하면 해당한다.
 * 부분 문자열 일치는 쓰지 않으므로 {@code hotel}·{@code telegram}·{@code headphone}·{@code mailbox_size}, {@code email_opt_in} 체크박스는 걸리지 않는다.
 * 필드 이름은 계약상 영문자·숫자·밑줄(ASCII)이라 한글 토큰은 두지 않는다. phone_model처럼 연락처가 아닌 텍스트 입력이 걸릴 수 있다(오탐은 가리는 쪽).
 */
public final class ContactLikeField {

    private static final Set<String> TEXT_LIKE_TYPES = Set.of("text", "tel", "email", "number");
    private static final Set<String> PHONE_TOKENS = Set.of("phone", "tel", "mobile", "hp", "cell", "telephone",
        "cellphone", "mobilephone", "phonenumber", "phoneno");
    private static final Set<String> EMAIL_TOKENS = Set.of("email", "mail", "emailaddress", "mailaddress", "useremail");
    private static final List<String> PHONE_PREFIXES = List.of("phone", "mobile");
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
            if (EMAIL_TOKENS.contains(lower) || lower.startsWith("email")) {
                return Kind.EMAIL;
            }
            phone |= PHONE_TOKENS.contains(lower) || PHONE_PREFIXES.stream().anyMatch(lower::startsWith);
        }
        return phone ? Kind.PHONE : null;
    }

    /** 예약 이름(phone·email)은 컬럼으로 가므로 제외한 추가 연락처의 종류. 경고와 마스킹이 이 한 메서드로 판단한다. */
    public static Kind extraKindOf(String name, String type) {
        return "phone".equals(name) || "email".equals(name) ? null : kindOf(name, type);
    }
}
