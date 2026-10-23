package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.stats.application.StatsService.CreatorCampaigns;
import java.util.List;

/** 한 크리에이터의 캠페인별 지표. 캠페인 행끼리는 방문자·전환 방문자를 합산하지 않는다(교집합은 캠페인 안에서만). */
public record CreatorCampaignStatsResponse(long creatorId, String name, String status, String from, String to,
        List<CampaignListStatsResponse.CampaignStatsItem> campaigns) {

    static CreatorCampaignStatsResponse of(String from, String to, CreatorCampaigns result) {
        Creator c = result.creator();
        return new CreatorCampaignStatsResponse(c.getId(), c.getName(), c.getStatus().value(), from, to,
            CampaignListStatsResponse.of(from, to, result.campaigns()).campaigns());
    }
}
