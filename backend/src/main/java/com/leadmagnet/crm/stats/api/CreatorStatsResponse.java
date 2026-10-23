package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.Metrics;
import com.leadmagnet.crm.stats.application.StatsService.ChannelMetrics;
import com.leadmagnet.crm.stats.application.StatsService.CreatorMetrics;
import java.math.BigDecimal;
import java.util.List;

/** 캠페인의 크리에이터별 지표. creator가 null인 행은 "직접·자체 링크"(크리에이터 없는 링크 + direct)다. */
public record CreatorStatsResponse(long campaignId, String from, String to, List<CreatorStatsItem> creators) {

    public record CreatorSummary(long id, String name, String status) {
    }

    public record CreatorStatsItem(CreatorSummary creator, long visits, long visitors, long leads, long convertedVisitors,
            BigDecimal conversionRate, List<ChannelStatsResponse.ChannelStatsItem> channels) {
    }

    static CreatorStatsResponse of(long campaignId, String from, String to, List<CreatorMetrics> rows) {
        return new CreatorStatsResponse(campaignId, from, to, rows.stream().map(r -> {
            Metrics m = r.metrics();
            CreatorSummary ref = r.creatorId() == null ? null : new CreatorSummary(r.creatorId(), r.name(), r.status());
            return new CreatorStatsItem(ref, m.visits(), m.visitors(), m.leads(), m.convertedVisitors(), m.conversionRate(),
                r.channels().stream().map(CreatorStatsResponse::channel).toList());
        }).toList());
    }

    private static ChannelStatsResponse.ChannelStatsItem channel(ChannelMetrics c) {
        Metrics m = c.metrics();
        return new ChannelStatsResponse.ChannelStatsItem(c.channel().value(), m.visits(), m.visitors(), m.leads(),
            m.convertedVisitors(), m.conversionRate());
    }
}
