package com.leadmagnet.crm.campaign.domain;

import java.util.Locale;

public enum CampaignStatus {
    ACTIVE, ARCHIVED;

    /** API 응답과 DB에 쓰는 소문자 값. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CampaignStatus fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
