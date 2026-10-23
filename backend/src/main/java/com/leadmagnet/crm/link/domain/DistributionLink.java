package com.leadmagnet.crm.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 폼 하나의 채널 하나(그리고 크리에이터 하나 또는 없음)에 대응하는 배포 링크. UTM은 생성 시 채널·폼 slug·크리에이터로 정해진다.
 * 크리에이터는 id로만 참조하고 바꿀 수 없다(이미 쌓인 방문·신청의 크리에이터 귀속이 이 값에서 나온다, ADR 0014).
 */
@Entity
@Table(name = "distribution_links")
public class DistributionLink {

    static final String UTM_MEDIUM = "social";
    static final String UTM_CONTENT_CREATOR_PREFIX = "creator-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false, updatable = false)
    private Long formId;

    @Convert(converter = ChannelConverter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(name = "creator_id", updatable = false)
    private Long creatorId;

    @Column(nullable = false, updatable = false, length = 16)
    private String code;

    @Column(name = "utm_source", updatable = false)
    private String utmSource;

    @Column(name = "utm_medium", updatable = false)
    private String utmMedium;

    @Column(name = "utm_campaign", updatable = false)
    private String utmCampaign;

    @Column(name = "utm_content", updatable = false)
    private String utmContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DistributionLink() {
    }

    private DistributionLink(Long formId, Channel channel, Long creatorId, String code, String formSlug, Instant now) {
        this.formId = formId;
        this.channel = channel;
        this.creatorId = creatorId;
        this.code = code;
        this.utmSource = channel.value();
        this.utmMedium = UTM_MEDIUM;
        this.utmCampaign = formSlug;
        this.utmContent = creatorId == null ? null : UTM_CONTENT_CREATOR_PREFIX + creatorId;
        this.createdAt = now;
    }

    /** 크리에이터 없는 링크(운영자 자체 링크). */
    public static DistributionLink create(Long formId, Channel channel, String code, String formSlug, Instant now) {
        return create(formId, channel, null, code, formSlug, now);
    }

    /** creatorId가 null이면 크리에이터 없는 링크다. */
    public static DistributionLink create(Long formId, Channel channel, Long creatorId, String code, String formSlug,
            Instant now) {
        return new DistributionLink(formId, channel, creatorId, code, formSlug, now);
    }

    public Long getId() {
        return id;
    }

    public Long getFormId() {
        return formId;
    }

    public Channel getChannel() {
        return channel;
    }

    /** 크리에이터 없는 링크면 null. */
    public Long getCreatorId() {
        return creatorId;
    }

    public String getCode() {
        return code;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    /** 크리에이터 링크는 {@code creator-{id}}, 자체 링크는 null. */
    public String getUtmContent() {
        return utmContent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
