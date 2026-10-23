package com.leadmagnet.crm.campaign.api;

import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.form.domain.Form;
import java.time.Instant;
import java.util.List;

public record CampaignResponse(Long id, String name, String description, String status, Instant startsAt,
        Instant endsAt, Instant createdAt, Instant updatedAt, List<FormSummary> forms) {

    public record FormSummary(Long id, String slug, String title, String status, Instant publishedAt,
            Instant closedAt) {
    }

    public static CampaignResponse from(Campaign c, List<Form> forms) {
        return new CampaignResponse(c.getId(), c.getName(), c.getDescription(), c.getStatus().value(),
            c.getStartsAt(), c.getEndsAt(), c.getCreatedAt(), c.getUpdatedAt(),
            forms.stream().map(f -> new FormSummary(f.getId(), f.getSlug(), f.getTitle(),
                f.getStatus().value(), f.getPublishedAt(), f.getClosedAt())).toList());
    }
}
