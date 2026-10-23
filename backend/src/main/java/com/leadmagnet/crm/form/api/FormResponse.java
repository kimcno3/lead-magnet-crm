package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.template.domain.FormField;
import java.time.Instant;
import java.util.List;

public record FormResponse(Long id, Long campaignId, Long templateId, String slug, String title, String consentText,
        List<FormField> fieldConfig, String status, Instant publishedAt, Instant closedAt, Instant createdAt) {

    public static FormResponse from(Form f) {
        return new FormResponse(f.getId(), f.getCampaignId(), f.getTemplateId(), f.getSlug(), f.getTitle(),
            f.getConsentText(), f.getFieldConfig(), f.getStatus().value(),
            f.getPublishedAt(), f.getClosedAt(), f.getCreatedAt());
    }
}
