package com.leadmagnet.crm.form.domain;

import java.util.Locale;

public enum FormStatus {
    DRAFT, PUBLISHED, CLOSED;

    /** API 응답과 DB에 쓰는 소문자 값. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FormStatus fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
