package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.Metrics;
import com.leadmagnet.crm.stats.application.StatsService.ChannelMetrics;
import java.math.BigDecimal;
import java.util.List;

public record ChannelStatsResponse(long campaignId, String from, String to, List<Item> channels) {

    public record Item(String channel, long visits, long visitors, long leads, long convertedVisitors,
            BigDecimal conversionRate) {
    }

    static ChannelStatsResponse of(long campaignId, String from, String to, List<ChannelMetrics> channels) {
        return new ChannelStatsResponse(campaignId, from, to, channels.stream().map(c -> {
            Metrics m = c.metrics();
            return new Item(c.channel().value(), m.visits(), m.visitors(), m.leads(), m.convertedVisitors(),
                m.conversionRate());
        }).toList());
    }
}
