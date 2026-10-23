package com.leadmagnet.crm.campaign.api;

import java.time.Instant;

/** retentionDays: 신청 보존 기간(일, 1~3650). 없으면 365 (ADR 0020). */
public record CreateCampaignRequest(String name, String description, Instant startsAt, Instant endsAt,
        Integer retentionDays) {
}
