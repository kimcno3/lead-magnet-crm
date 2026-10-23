package com.leadmagnet.crm.link.api;

import com.leadmagnet.crm.link.domain.DistributionLink;
import java.time.Instant;

public record LinkResponse(Long id, String channel, String code, String url, String utmSource, String utmMedium,
        String utmCampaign, Instant createdAt) {

    public static LinkResponse from(DistributionLink l, String publicBaseUrl) {
        return new LinkResponse(l.getId(), l.getChannel().value(), l.getCode(),
            publicBaseUrl + "/l/" + l.getCode(), l.getUtmSource(), l.getUtmMedium(), l.getUtmCampaign(),
            l.getCreatedAt());
    }
}
