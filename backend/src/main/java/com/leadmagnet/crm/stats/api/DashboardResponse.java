package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.DashboardService.Dashboard;
import com.leadmagnet.crm.stats.application.Metrics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 유입 현황 대시보드. from·to는 적용된 기간(기본값을 채운 값)이다. recentLeads에는 가린 이름만 있고 전화번호·이메일은 없다.
 */
public record DashboardResponse(String from, String to, MetricsItem totals, List<DailyItem> daily, List<FormItem> forms,
        List<ChannelStatsResponse.ChannelStatsItem> channels, List<CreatorItem> creators,
        List<RecentLeadItem> recentLeads) {

    public record MetricsItem(long visits, long visitors, long leads, long convertedVisitors, BigDecimal conversionRate) {

        static MetricsItem of(Metrics m) {
            return new MetricsItem(m.visits(), m.visitors(), m.leads(), m.convertedVisitors(), m.conversionRate());
        }
    }

    @Schema(description = "서울 날짜 하루. convertedVisitors는 그날 방문하고 같은 날 같은 캠페인에 신청한 방문자 수")
    public record DailyItem(String date, long visits, long visitors, long leads, long convertedVisitors) {
    }

    @Schema(description = "기간 안에 방문이나 신청이 있는 폼. 전환은 같은 폼 안의 교집합")
    public record FormItem(long campaignId, String campaignName, long formId, String title, String status, long visits,
            long visitors, long leads, long convertedVisitors, BigDecimal conversionRate) {
    }

    @Schema(description = "creator가 null이면 직접·자체 링크(크리에이터 없는 링크 + direct)")
    public record CreatorItem(CreatorStatsResponse.CreatorSummary creator, long visits, long visitors, long leads,
            long convertedVisitors, BigDecimal conversionRate) {
    }

    @Schema(description = "최근 신청. name은 가린 이름(이름이 없거나 파기·복호화 불가면 null). 전화번호·이메일은 주지 않는다")
    public record RecentLeadItem(long id, Instant createdAt, long campaignId, String campaignName, String formTitle,
            String channel, String creatorName, String name) {
    }

    static DashboardResponse of(Dashboard d) {
        return new DashboardResponse(d.period().from().toString(), d.period().to().toString(),
            MetricsItem.of(d.totals()),
            d.daily().stream().map(r -> {
                Metrics m = r.metrics();
                return new DailyItem(r.date().toString(), m.visits(), m.visitors(), m.leads(), m.convertedVisitors());
            }).toList(),
            d.forms().stream().map(r -> {
                Metrics m = r.metrics();
                return new FormItem(r.campaignId(), r.campaignName(), r.formId(), r.title(), r.status(), m.visits(),
                    m.visitors(), m.leads(), m.convertedVisitors(), m.conversionRate());
            }).toList(),
            d.channels().stream().map(c -> {
                Metrics m = c.metrics();
                return new ChannelStatsResponse.ChannelStatsItem(c.channel().value(), m.visits(), m.visitors(),
                    m.leads(), m.convertedVisitors(), m.conversionRate());
            }).toList(),
            d.creators().stream().map(r -> {
                Metrics m = r.metrics();
                CreatorStatsResponse.CreatorSummary ref = r.creatorId() == null ? null
                    : new CreatorStatsResponse.CreatorSummary(r.creatorId(), r.name(), r.status());
                return new CreatorItem(ref, m.visits(), m.visitors(), m.leads(), m.convertedVisitors(),
                    m.conversionRate());
            }).toList(),
            d.recentLeads().stream().map(r -> new RecentLeadItem(r.id(), r.createdAt(), r.campaignId(),
                r.campaignName(), r.formTitle(), r.channel(), r.creatorName(), r.name())).toList());
    }
}
