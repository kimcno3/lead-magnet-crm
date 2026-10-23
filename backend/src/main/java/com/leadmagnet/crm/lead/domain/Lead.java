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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공개 폼으로 접수한 신청 한 건 (ADR 0008). 연락처 핵심 필드는 컬럼, 나머지 입력은 payload다.
 * 접수 뒤에는 보존 기간 파기({@link #anonymize})로만 바뀐다. 입력은 {@link SubmissionValidator}가 검증·정규화한 값만 받는다.
 * 연락처는 암호문·해시 컬럼에만 둔다(ADR 0015).
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

    @Column(name = "name_enc")
    private byte[] nameEnc;

    @Column(name = "phone_enc")
    private byte[] phoneEnc;

    @Column(name = "email_enc")
    private byte[] emailEnc;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "consent_at", nullable = false, updatable = false)
    private Instant consentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consent_text", updatable = false, columnDefinition = "text")
    private String consentText;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "consent_sha256", updatable = false, length = 64)
    private String consentSha256;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected Lead() {
    }

    private Lead(Long formId, Long campaignId, Attribution attribution, UUID visitorId, Submission submission,
            ProtectedContact contact, ConsentEvidence consent, Instant now) {
        this.formId = formId;
        this.campaignId = campaignId;
        this.linkId = attribution.linkId();
        this.channel = attribution.channel();
        this.visitorId = visitorId;
        this.nameEnc = contact.nameEnc();
        this.phoneEnc = contact.phoneEnc();
        this.emailEnc = contact.emailEnc();
        this.phoneHash = contact.phoneHash();
        this.emailHash = contact.emailHash();
        this.payload = Map.copyOf(submission.payload());
        this.consentAt = now;
        this.createdAt = now;
        this.consentText = consent.text();
        this.consentSha256 = consent.sha256();
    }

    /**
     * 동의는 제출 시점에 확인했으므로 consentAt은 접수 시각이다. visitorId는 쿠키가 없거나 형식이 틀리면 null.
     * contact는 같은 submission의 연락처를 암호화·해시한 값이다. consent는 제출 시점 폼의 동의 문구다(ADR 0020).
     */
    public static Lead receive(Long formId, Long campaignId, Attribution attribution, UUID visitorId,
            Submission submission, ProtectedContact contact, ConsentEvidence consent, Instant now) {
        return new Lead(formId, campaignId, attribution, visitorId, submission, contact, consent, now);
    }

    /**
     * 보존 기간이 지난 신청을 익명화한다(ADR 0020). 이름·연락처 암호문과 중복 판정 해시를 지우고, payload는 키만 남겨
     * 값을 null(목록이면 빈 목록)로 바꾼다. 지표에 쓰는 값(form·campaign·link·channel·visitor·created_at)과
     * 동의 증빙(문구·sha256·consent_at)은 그대로 둔다. 이미 익명화된 신청이면 아무것도 바꾸지 않고 false다.
     */
    public boolean anonymize(Instant now) {
        if (anonymizedAt != null) {
            return false;
        }
        this.nameEnc = null;
        this.phoneEnc = null;
        this.emailEnc = null;
        this.phoneHash = null;
        this.emailHash = null;
        this.payload = blankValues(payload);
        this.anonymizedAt = now;
        return true;
    }

    private static Map<String, Object> blankValues(Map<String, Object> payload) {
        Map<String, Object> blanked = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> blanked.put(key, value instanceof List<?> ? List.of() : null));
        }
        return Collections.unmodifiableMap(blanked);
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

    /** 저장용 연락처(암호문·해시). 값이 없는 필드는 null이다. */
    public ProtectedContact getProtectedContact() {
        return new ProtectedContact(nameEnc, phoneEnc, emailEnc, phoneHash, emailHash);
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

    /** 동의 문구 원문. V10 이전 신청은 백필 값이고, 백필할 수 없었으면 null이다. */
    public String getConsentText() {
        return consentText;
    }

    public String getConsentSha256() {
        return consentSha256;
    }

    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }
}
