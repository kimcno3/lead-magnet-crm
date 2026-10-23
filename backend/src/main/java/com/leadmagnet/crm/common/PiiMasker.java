package com.leadmagnet.crm.common;

/**
 * 로그에 남길 개인정보를 가린다. 전화번호·이메일 원문은 로그에 남기지 않는다.
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
}
