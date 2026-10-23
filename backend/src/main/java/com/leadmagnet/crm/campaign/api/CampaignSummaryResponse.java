package com.leadmagnet.crm.campaign.api;

import com.leadmagnet.crm.campaign.application.CampaignService.CampaignSummary;
import java.time.Instant;

public record CampaignSummaryResponse(Long id, String name, String status, Instant startsAt, Instant endsAt,
        long formCount, Instant createdAt) {

    public static CampaignSummaryResponse from(CampaignSummary s) {
        return new CampaignSummaryResponse(s.campaign().getId(), s.campaign().getName(),
            s.campaign().getStatus().value(), s.campaign().getStartsAt(), s.campaign().getEndsAt(), s.formCount(),
            s.campaign().getCreatedAt());
    }
}
