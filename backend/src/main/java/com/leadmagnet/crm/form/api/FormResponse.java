package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.template.domain.FormField;
import java.time.Instant;
import java.util.List;

/**
 * publishedTemplateSha256은 게시 시점에 복사한 템플릿의 sha256(draft는 null), templateSha256은 템플릿의 현재 sha256이다.
 * 둘이 다르면 게시한 뒤 템플릿에 새 버전이 올라온 것이다(ADR 0018).
 */
public record FormResponse(Long id, Long campaignId, Long templateId, String slug, String title, String consentText,
        List<FormField> fieldConfig, String status, Instant publishedAt, Instant closedAt, Instant createdAt,
        String publishedTemplateSha256, String templateSha256) {

    public static FormResponse from(Form f, String currentTemplateSha256) {
        return new FormResponse(f.getId(), f.getCampaignId(), f.getTemplateId(), f.getSlug(), f.getTitle(),
            f.getConsentText(), f.getFieldConfig(), f.getStatus().value(),
            f.getPublishedAt(), f.getClosedAt(), f.getCreatedAt(), f.getPublishedTemplateSha256(), currentTemplateSha256);
    }
}
