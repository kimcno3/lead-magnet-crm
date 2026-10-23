package com.leadmagnet.crm.creator.domain;

import java.util.Locale;

public enum CreatorStatus {
    ACTIVE, ARCHIVED;

    /** API 응답과 DB에 쓰는 소문자 값. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CreatorStatus fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
