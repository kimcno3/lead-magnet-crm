package com.leadmagnet.crm.common;

import java.util.regex.Pattern;

/**
 * 국내 전화번호 표시 형식. 관리자 화면의 formatPhone(admin-web/src/format.ts)과 같은 규칙이다.
 * CSV 원문 열에 쓴다: 숫자만 있으면 스프레드시트가 숫자로 읽어 앞의 0이 사라진다(ADR 0016).
 */
public final class PhoneFormat {

    private static final Pattern DOMESTIC = Pattern.compile("0\\d{8,10}");

    private PhoneFormat() {
    }

    /** 하이픈을 넣은 번호. 국내 번호 모양(0으로 시작하는 9~11자리)이 아니면 받은 값 그대로, null은 null. */
    public static String hyphenate(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        String digits = phone.replace("-", "");
        if (!DOMESTIC.matcher(digits).matches()) {
            return phone;
        }
        int areaLength = digits.startsWith("02") ? 2 : 3;
        String rest = digits.substring(areaLength);
        int mid = rest.length() == 8 ? 4 : 3;
        return digits.substring(0, areaLength) + "-" + rest.substring(0, mid) + "-" + rest.substring(mid);
    }
}
