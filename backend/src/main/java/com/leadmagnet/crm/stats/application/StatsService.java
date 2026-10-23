package com.leadmagnet.crm.stats.application;

import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.stats.domain.DateRange;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 성과 지표 조회 (ADR 0005). 기간은 yyyy-MM-dd 문자열로 받아 DateRange가 검증한다. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final StatsQuery query;
    private final CampaignRepository campaigns;

    public StatsService(StatsQuery query, CampaignRepository campaigns) {
        this.query = query;
        this.campaigns = campaigns;
    }

    public record ChannelMetrics(AttributionChannel channel, Metrics metrics) {
    }

    public record CampaignMetrics(long campaignId, String name, String status, Metrics metrics) {
    }

    /** 한 캠페인의 전체 지표. 없는 캠페인은 404. */
    public Metrics campaign(long campaignId, String from, String to) {
        DateRange range = DateRange.parse(from, to);
        return query.byCampaign(campaignId, range).stream().findFirst()
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)).metrics();
    }

    /** 한 캠페인의 채널별 지표. 다섯 채널을 선언 순서대로 항상 돌려주고 데이터가 없으면 0이다. */
    public List<ChannelMetrics> channels(long campaignId, String from, String to) {
        DateRange range = DateRange.parse(from, to);
        if (!campaigns.existsById(campaignId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        Map<String, Metrics> byChannel = query.byChannel(campaignId, range);
        return Arrays.stream(AttributionChannel.values())
            .map(c -> new ChannelMetrics(c, byChannel.getOrDefault(c.value(), Metrics.ZERO)))
            .toList();
    }

    /** 모든 캠페인의 지표. 최신 캠페인순이고 데이터가 없는 캠페인도 포함한다. */
    public List<CampaignMetrics> campaigns(String from, String to) {
        DateRange range = DateRange.parse(from, to);
        return query.byCampaign(null, range).stream()
            .map(r -> new CampaignMetrics(r.campaignId(), r.name(), r.status(), r.metrics()))
            .toList();
    }
}
