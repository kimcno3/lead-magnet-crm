package com.leadmagnet.crm.stats.application;

import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.DateRange;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.creator.application.CreatorService;
import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.link.domain.Channel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 성과 지표 조회 (ADR 0005). 기간은 yyyy-MM-dd 문자열로 받아 DateRange가 검증한다. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final StatsQuery query;
    private final CampaignRepository campaigns;
    private final CreatorService creators;

    public StatsService(StatsQuery query, CampaignRepository campaigns, CreatorService creators) {
        this.query = query;
        this.campaigns = campaigns;
        this.creators = creators;
    }

    public record ChannelMetrics(AttributionChannel channel, Metrics metrics) {
    }

    public record CampaignMetrics(long campaignId, String name, String status, Metrics metrics) {
    }

    /**
     * 캠페인 안의 크리에이터 한 행. creatorId가 null이면 "직접·자체 링크"(크리에이터 없는 링크 + direct)이고
     * name·status도 null이다. channels는 크리에이터 행이면 링크 채널 네 개, 직접·자체 링크 행이면 direct까지 다섯 개.
     */
    public record CreatorMetrics(Long creatorId, String name, String status, Metrics metrics,
            List<ChannelMetrics> channels) {
    }

    public record CreatorCampaigns(Creator creator, List<CampaignMetrics> campaigns) {
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

    /**
     * 한 캠페인의 크리에이터별 지표(ADR 0014). 그 캠페인에 링크가 있는 크리에이터를 등록순으로, 마지막에 직접·자체 링크 행.
     * 전환은 같은 크리에이터 안의 교집합이고 채널 행은 같은 크리에이터·같은 채널 안의 교집합이다. 없는 캠페인은 404.
     */
    public List<CreatorMetrics> creators(long campaignId, String from, String to) {
        DateRange range = DateRange.parse(from, to);
        if (!campaigns.existsById(campaignId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        Map<Long, Map<String, Metrics>> byPair = new HashMap<>();
        for (StatsQuery.CreatorChannelRow r : query.byCreatorChannel(campaignId, range)) {
            byPair.computeIfAbsent(key(r.creatorId()), k -> new HashMap<>()).put(r.channel(), r.metrics());
        }
        return query.byCreator(campaignId, range).stream().map(r -> {
            Map<String, Metrics> channels = byPair.getOrDefault(key(r.creatorId()), Map.of());
            List<AttributionChannel> shown = r.creatorId() == null
                ? Arrays.asList(AttributionChannel.values())
                : Arrays.stream(Channel.values()).map(AttributionChannel::of).toList();
            return new CreatorMetrics(r.creatorId(), r.name(), r.status(), r.metrics(), shown.stream()
                .map(c -> new ChannelMetrics(c, channels.getOrDefault(c.value(), Metrics.ZERO))).toList());
        }).toList();
    }

    /** 한 크리에이터의 캠페인별 지표. 링크가 있는 캠페인만 최신순으로. 없는 크리에이터는 404. */
    public CreatorCampaigns creatorCampaigns(long creatorId, String from, String to) {
        DateRange range = DateRange.parse(from, to);
        Creator creator = creators.get(creatorId);
        return new CreatorCampaigns(creator, query.creatorByCampaign(creatorId, range).stream()
            .map(r -> new CampaignMetrics(r.campaignId(), r.name(), r.status(), r.metrics()))
            .toList());
    }

    /** 직접·자체 링크(null)도 맵 키로 쓰려고 0으로 바꾼다. */
    private static long key(Long creatorId) {
        return Objects.requireNonNullElse(creatorId, 0L);
    }

    /** 모든 캠페인의 지표. 최신 캠페인순이고 데이터가 없는 캠페인도 포함한다. */
    public List<CampaignMetrics> campaigns(String from, String to) {
        DateRange range = DateRange.parse(from, to);
        return query.byCampaign(null, range).stream()
            .map(r -> new CampaignMetrics(r.campaignId(), r.name(), r.status(), r.metrics()))
            .toList();
    }
}
