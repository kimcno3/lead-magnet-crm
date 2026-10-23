package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.form.domain.Slug;
import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FormService {

    private static final int SLUG_ATTEMPTS = 5;
    private static final String PUBLISHED = "published";
    private static final String CLOSED = "closed";

    private final FormRepository forms;
    private final CampaignRepository campaigns;
    private final TemplateService templates;
    private final Clock clock;

    public FormService(FormRepository forms, CampaignRepository campaigns, TemplateService templates, Clock clock) {
        this.forms = forms;
        this.campaigns = campaigns;
        this.templates = templates;
        this.clock = clock;
    }

    public Form create(long campaignId, Long templateId, String title, String consentText, String requestedSlug) {
        Campaign campaign = campaigns.findById(campaignId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (campaign.isArchived()) {
            throw new DomainException(ErrorCode.CAMPAIGN_ARCHIVED);
        }
        if (templateId == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "templateId가 필요합니다");
        }
        HtmlTemplate template = templates.get(templateId);
        Slug slug = requestedSlug != null ? new Slug(requestedSlug) : randomFreeSlug();
        if (forms.existsBySlug(slug.value())) {
            throw new DomainException(ErrorCode.SLUG_TAKEN);
        }
        Form form = Form.create(campaignId, templateId, slug, title, consentText, template.getFields(), clock.instant());
        try {
            return forms.saveAndFlush(form);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("ux_forms_slug")) {
                throw new DomainException(ErrorCode.SLUG_TAKEN);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Form get(long id) {
        return find(id);
    }

    /** null인 필드는 바꾸지 않는다. 내용 수정은 draft에서만, status는 "published"·"closed"만 받는다. */
    public Form update(long id, String title, String consentText, List<FormField> fieldConfig, String status) {
        Form form = find(id);
        if (status != null && !PUBLISHED.equals(status) && !CLOSED.equals(status)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "status는 \"published\" 또는 \"closed\"만 지정할 수 있습니다");
        }
        Instant now = clock.instant();
        if (title != null || consentText != null || fieldConfig != null) {
            form.edit(title, consentText, fieldConfig, now);
        }
        if (PUBLISHED.equals(status)) {
            form.publish(now);
        } else if (CLOSED.equals(status)) {
            form.close(now);
        }
        return form;
    }

    private Slug randomFreeSlug() {
        for (int i = 0; i < SLUG_ATTEMPTS; i++) {
            Slug candidate = Slug.random();
            if (!forms.existsBySlug(candidate.value())) {
                return candidate;
            }
        }
        throw new DomainException(ErrorCode.SLUG_TAKEN);
    }

    private Form find(long id) {
        return forms.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
