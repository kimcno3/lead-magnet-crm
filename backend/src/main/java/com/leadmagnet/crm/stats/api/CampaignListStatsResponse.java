package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.Metrics;
import com.leadmagnet.crm.stats.application.StatsService.CampaignMetrics;
import java.math.BigDecimal;
import java.util.List;

public record CampaignListStatsResponse(String from, String to, List<Item> campaigns) {

    public record Item(long campaignId, String name, String status, long visits, long visitors, long leads,
            long convertedVisitors, BigDecimal conversionRate) {
    }

    static CampaignListStatsResponse of(String from, String to, List<CampaignMetrics> campaigns) {
        return new CampaignListStatsResponse(from, to, campaigns.stream().map(c -> {
            Metrics m = c.metrics();
            return new Item(c.campaignId(), c.name(), c.status(), m.visits(), m.visitors(), m.leads(),
                m.convertedVisitors(), m.conversionRate());
        }).toList());
    }
}
