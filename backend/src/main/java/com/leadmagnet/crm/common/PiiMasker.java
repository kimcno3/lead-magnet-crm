package com.leadmagnet.crm.common;

/**
 * 개인정보를 가린다. 전화번호·이메일 원문은 로그에 남기지 않는다.
 * {@code mask*}(이름 제외)는 로그용으로 빈 값을 ""로, {@code *ForDisplay}와 {@link #maskName}은 명단 화면용으로 빈 값을 null로 둔다 (ADR 0015).
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /** {@code operator@example.com} → {@code o***@example.com} */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** {@code 010-1234-5678} → {@code ***-****-5678}. 숫자 끝 네 자리만 남긴다. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***-****-" + digits.substring(digits.length() - 4);
    }

    /** 화면용. {@code 홍길동} → {@code 홍*동}, 두 글자 {@code 에이} → {@code 에*}, 한 글자 → {@code *}. 비었으면 null. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int[] chars = name.strip().codePoints().toArray();
        return switch (chars.length) {
            case 1 -> "*";
            case 2 -> Character.toString(chars[0]) + "*";
            default -> Character.toString(chars[0]) + "*" + Character.toString(chars[chars.length - 1]);
        };
    }

    /** 화면용. {@code 01012345678} → {@code 010-****-5678}. 숫자가 8자리 미만이면 {@code ****}, 없으면 null. */
    public static String maskPhoneForDisplay(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /** 화면용. {@link #maskEmail}과 같은 형식이고 비었으면 null. */
    public static String maskEmailForDisplay(String email) {
        return email == null || email.isBlank() ? null : maskEmail(email);
    }
}
