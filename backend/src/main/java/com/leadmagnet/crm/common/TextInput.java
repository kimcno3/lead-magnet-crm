package com.leadmagnet.crm.common;

/**
 * 자유 입력 문자열 공통 규칙. PostgreSQL text·jsonb는 NUL(U+0000)을 저장하지 못해 500이 되고,
 * 그때 DB 오류 문맥이 입력값(다른 개인정보 포함)을 로그에 남긴다. 그래서 저장 전에 C0 제어 문자를 막는다.
 */
public final class TextInput {

    private TextInput() {
    }

    /** U+0000~U+001F 중 탭(\t)·줄바꿈(\n)·캐리지 리턴(\r)을 뺀 제어 문자가 있으면 true. null은 false. */
    public static boolean containsForbiddenControl(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }
}
