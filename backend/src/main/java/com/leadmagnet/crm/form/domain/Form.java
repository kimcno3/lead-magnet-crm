package com.leadmagnet.crm.form.domain;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.template.domain.FormField;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 캠페인과 템플릿으로 만든 공개 신청 폼. draft → published → closed 순으로만 전이하고,
 * 내용(제목, 동의 문구, 필드의 label·required)은 draft에서만 고친다.
 */
@Entity
@Table(name = "forms")
public class Form {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_CONSENT_LENGTH = 2000;
    static final int MAX_LABEL_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private Long campaignId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(nullable = false, updatable = false, length = 64)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "consent_text", nullable = false, columnDefinition = "text")
    private String consentText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", nullable = false, columnDefinition = "jsonb")
    private List<FormField> fieldConfig;

    @Convert(converter = FormStatusConverter.class)
    @Column(nullable = false, length = 20)
    private FormStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Form() {
    }

    private Form(Long campaignId, Long templateId, Slug slug, String title, String consentText,
            List<FormField> templateFields, Instant now) {
        validateTitle(title);
        validateConsent(consentText);
        this.campaignId = campaignId;
        this.templateId = templateId;
        this.slug = slug.value();
        this.title = title.trim();
        this.consentText = consentText;
        this.fieldConfig = List.copyOf(templateFields);
        this.status = FormStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** templateFields는 템플릿 fields의 스냅샷으로 복사해 둔다. 이후 템플릿이 바뀌어도 폼은 영향받지 않는다. */
    public static Form create(Long campaignId, Long templateId, Slug slug, String title, String consentText,
            List<FormField> templateFields, Instant now) {
        return new Form(campaignId, templateId, slug, title, consentText, templateFields, now);
    }

    public void publish(Instant now) {
        if (status != FormStatus.DRAFT) {
            throw new DomainException(ErrorCode.INVALID_FORM_TRANSITION);
        }
        status = FormStatus.PUBLISHED;
        publishedAt = now;
        updatedAt = now;
    }

    public void close(Instant now) {
        if (status != FormStatus.PUBLISHED) {
            throw new DomainException(ErrorCode.INVALID_FORM_TRANSITION);
        }
        status = FormStatus.CLOSED;
        closedAt = now;
        updatedAt = now;
    }

    /**
     * draft에서만 호출할 수 있다. null인 인자는 바꾸지 않는다.
     * fieldConfig는 기존 필드와 이름이 정확히 같아야 하고, 필드마다 label·required만 반영한다(type·options는 유지).
     * 검증을 모두 마친 뒤에 값을 바꾸므로 실패하면 아무것도 바뀌지 않는다.
     */
    public void edit(String title, String consentText, List<FieldChange> fieldConfig, Instant now) {
        if (status != FormStatus.DRAFT) {
            throw new DomainException(ErrorCode.FORM_NOT_EDITABLE);
        }
        if (title != null) {
            validateTitle(title);
        }
        if (consentText != null) {
            validateConsent(consentText);
        }
        List<FormField> merged = fieldConfig != null ? mergeFields(fieldConfig) : null;
        if (title != null) {
            this.title = title.trim();
        }
        if (consentText != null) {
            this.consentText = consentText;
        }
        if (merged != null) {
            this.fieldConfig = merged;
        }
        this.updatedAt = now;
    }

    private List<FormField> mergeFields(List<FieldChange> requested) {
        Set<String> seen = new HashSet<>();
        for (FieldChange field : requested) {
            if (field == null || field.name() == null || !seen.add(field.name())) {
                throw invalid("fieldConfig", "fieldConfig의 필드 이름이 올바르지 않거나 중복되었습니다");
            }
            if (field.label() != null && (field.label().isBlank() || field.label().length() > MAX_LABEL_LENGTH)) {
                throw invalid("fieldConfig", "필드 label은 1~" + MAX_LABEL_LENGTH + "자여야 합니다: " + field.name());
            }
        }
        Set<String> existing = new HashSet<>();
        fieldConfig.forEach(f -> existing.add(f.name()));
        if (!existing.equals(seen)) {
            throw invalid("fieldConfig", "fieldConfig는 필드를 추가·삭제하거나 이름을 바꿀 수 없습니다");
        }
        return fieldConfig.stream()
            .map(current -> {
                FieldChange change = requested.stream().filter(r -> r.name().equals(current.name())).findFirst().orElseThrow();
                return new FormField(current.name(), current.type(),
                    change.required() != null ? change.required() : current.required(),
                    change.label() != null ? change.label().trim() : current.label(), current.options());
            })
            .toList();
    }

    /** 제목·동의 문구 규칙. 생성 전에 다른 검사보다 먼저 호출할 수 있다. */
    public static void validateContent(String title, String consentText) {
        validateTitle(title);
        validateConsent(consentText);
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
            throw invalid("title", "title은 1~" + MAX_TITLE_LENGTH + "자여야 합니다");
        }
    }

    private static void validateConsent(String consentText) {
        if (consentText == null || consentText.isBlank() || consentText.length() > MAX_CONSENT_LENGTH) {
            throw invalid("consentText", "consentText는 1~" + MAX_CONSENT_LENGTH + "자여야 합니다");
        }
    }

    private static DomainException invalid(String field, String detail) {
        return DomainException.validation(field, detail);
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getConsentText() {
        return consentText;
    }

    public List<FormField> getFieldConfig() {
        return fieldConfig;
    }

    public FormStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
