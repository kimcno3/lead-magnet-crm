package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.Metrics;
import java.math.BigDecimal;

public record CampaignStatsResponse(long campaignId, String from, String to, long visits, long visitors, long leads,
        long convertedVisitors, BigDecimal conversionRate) {

    static CampaignStatsResponse of(long campaignId, String from, String to, Metrics m) {
        return new CampaignStatsResponse(campaignId, from, to, m.visits(), m.visitors(), m.leads(),
            m.convertedVisitors(), m.conversionRate());
    }
}
