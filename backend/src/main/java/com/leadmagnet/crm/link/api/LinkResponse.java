package com.leadmagnet.crm.link.api;

import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.link.application.LinkService.LinkDetail;
import com.leadmagnet.crm.link.domain.DistributionLink;
import java.time.Instant;

/** creator는 크리에이터 없는 링크면 null이다. */
public record LinkResponse(Long id, String channel, CreatorRef creator, String code, String url, String utmSource,
        String utmMedium, String utmCampaign, Instant createdAt) {

    public record CreatorRef(Long id, String name) {
    }

    public static LinkResponse from(LinkDetail detail, String publicBaseUrl) {
        DistributionLink l = detail.link();
        Creator c = detail.creator();
        return new LinkResponse(l.getId(), l.getChannel().value(), c == null ? null : new CreatorRef(c.getId(), c.getName()),
            l.getCode(), publicBaseUrl + "/l/" + l.getCode(), l.getUtmSource(), l.getUtmMedium(), l.getUtmCampaign(),
            l.getCreatedAt());
    }
}
