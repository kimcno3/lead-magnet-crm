package com.leadmagnet.crm.lead.domain;

import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.link.domain.AttributionChannelConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공개 폼으로 접수한 신청 한 건 (ADR 0008). 연락처 핵심 필드는 컬럼, 나머지 입력은 payload다.
 * 접수 뒤에는 바꾸지 않는다. 입력은 {@link SubmissionValidator}가 검증·정규화한 값만 받는다.
 */
@Entity
@Table(name = "leads")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false, updatable = false)
    private Long formId;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private Long campaignId;

    @Column(name = "link_id", updatable = false)
    private Long linkId;

    @Convert(converter = AttributionChannelConverter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private AttributionChannel channel;

    @Column(name = "visitor_id", updatable = false)
    private UUID visitorId;

    @Column(updatable = false, length = 100)
    private String name;

    @Column(updatable = false, length = 20)
    private String phone;

    @Column(updatable = false, length = 254)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "consent_at", nullable = false, updatable = false)
    private Instant consentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Lead() {
    }

    private Lead(Long formId, Long campaignId, Attribution attribution, UUID visitorId, Submission submission,
            Instant now) {
        this.formId = formId;
        this.campaignId = campaignId;
        this.linkId = attribution.linkId();
        this.channel = attribution.channel();
        this.visitorId = visitorId;
        this.name = submission.name();
        this.phone = submission.phone();
        this.email = submission.email();
        this.payload = Map.copyOf(submission.payload());
        this.consentAt = now;
        this.createdAt = now;
    }

    /** 동의는 제출 시점에 확인했으므로 consentAt은 접수 시각이다. visitorId는 쿠키가 없거나 형식이 틀리면 null. */
    public static Lead receive(Long formId, Long campaignId, Attribution attribution, UUID visitorId,
            Submission submission, Instant now) {
        return new Lead(formId, campaignId, attribution, visitorId, submission, now);
    }

    public Long getId() {
        return id;
    }

    public Long getFormId() {
        return formId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public AttributionChannel getChannel() {
        return channel;
    }

    public UUID getVisitorId() {
        return visitorId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
