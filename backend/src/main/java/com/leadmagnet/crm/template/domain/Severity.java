package com.leadmagnet.crm.template.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 검사 결과의 심각도. JSON에는 소문자(warning|info)로 나간다. */
public enum Severity {
    WARNING("warning"),
    INFO("info");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
