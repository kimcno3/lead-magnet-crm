package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.form.domain.FieldChange;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.form.domain.Slug;
import com.leadmagnet.crm.template.application.TemplateService;
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
    private final AuditService audit;
    private final Clock clock;

    public FormService(FormRepository forms, CampaignRepository campaigns, TemplateService templates,
            AuditService audit, Clock clock) {
        this.forms = forms;
        this.campaigns = campaigns;
        this.templates = templates;
        this.audit = audit;
        this.clock = clock;
    }

    public Form create(long campaignId, Long templateId, String title, String consentText, String requestedSlug) {
        Campaign campaign = campaigns.findById(campaignId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (campaign.isArchived()) {
            throw new DomainException(ErrorCode.CAMPAIGN_ARCHIVED);
        }
        if (templateId == null) {
            throw DomainException.validation("templateId", "templateId가 필요합니다");
        }
        Form.validateContent(title, consentText);
        // 새 버전 업로드가 이 템플릿의 draft 폼을 다시 스냅샷하는 중이면 끝날 때까지 기다린다(ADR 0018 잠금 경로 1).
        HtmlTemplate template = templates.getForUpdate(templateId);
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

    /**
     * null인 필드는 바꾸지 않는다. 내용 수정은 draft에서만, status는 "published"·"closed"만 받는다. 게시·마감은 같은
     * 트랜잭션에서 form.publish·form.close를 남긴다(기록이 실패하면 상태 변경도 롤백, ADR 0016 변경 기록).
     */
    public Form update(long id, String title, String consentText, List<FieldChange> fieldConfig, String status,
            AuditActor actor) {
        // 잠금 순서는 템플릿 → 폼이다. 새 버전 업로드도 같은 순서라 교착이 없고, 게시는 새 버전이 커밋된 뒤의
        // 템플릿과 필드를 함께 읽는다(ADR 0018).
        Long templateId = forms.findTemplateIdById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        HtmlTemplate template = templates.getForUpdate(templateId);
        Form form = forms.findByIdForUpdate(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (status != null && !PUBLISHED.equals(status) && !CLOSED.equals(status)) {
            throw DomainException.validation("status", "status는 \"published\" 또는 \"closed\"만 지정할 수 있습니다");
        }
        Instant now = clock.instant();
        if (title != null || consentText != null || fieldConfig != null) {
            form.edit(title, consentText, fieldConfig, now);
        }
        if (PUBLISHED.equals(status)) {
            form.publish(now, template.getContent(), template.getSha256());
            audit.recordRequired(actor, AuditEvent.formStatusChange(true, id, form.getCampaignId(), form.getSlug()));
        } else if (CLOSED.equals(status)) {
            form.close(now);
            audit.recordRequired(actor, AuditEvent.formStatusChange(false, id, form.getCampaignId(), form.getSlug()));
        }
        return form;
    }

    /** 폼의 템플릿이 지금 가진 sha256. 게시 시점 스냅샷과 비교해 템플릿이 바뀌었는지 알리는 데 쓴다. */
    @Transactional(readOnly = true)
    public String currentTemplateSha256(Form form) {
        return templates.get(form.getTemplateId()).getSha256();
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
