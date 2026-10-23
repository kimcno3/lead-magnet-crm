package com.leadmagnet.crm.audit.domain;

import java.util.Arrays;
import java.util.Optional;

/** 감사 로그 action 값 (ADR 0016). DB와 API에는 {@link #value()}를 쓴다. */
public enum AuditAction {
    LEAD_LIST("lead.list"),
    LEAD_CONTACT_VIEW("lead.contact.view"),
    LEAD_EXPORT("lead.export"),
    LEAD_EXPORT_REVEAL("lead.export.reveal"),
    AUTH_LOGIN_SUCCESS("auth.login.success"),
    AUTH_LOGIN_FAILURE("auth.login.failure"),
    AUTH_LOGIN_LOCKED("auth.login.locked"),
    RETENTION_ANONYMIZE("retention.anonymize"),
    CAMPAIGN_RETENTION_UPDATE("campaign.retention.update");

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<AuditAction> fromValue(String value) {
        return Arrays.stream(values()).filter(a -> a.value.equals(value)).findFirst();
    }
}
