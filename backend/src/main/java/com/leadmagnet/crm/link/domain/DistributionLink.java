package com.leadmagnet.crm.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 폼 하나의 채널 하나에 대응하는 배포 링크. UTM은 생성 시 채널과 폼 slug로 정해진다. */
@Entity
@Table(name = "distribution_links")
public class DistributionLink {

    static final String UTM_MEDIUM = "social";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false, updatable = false)
    private Long formId;

    @Convert(converter = ChannelConverter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(nullable = false, updatable = false, length = 16)
    private String code;

    @Column(name = "utm_source", updatable = false)
    private String utmSource;

    @Column(name = "utm_medium", updatable = false)
    private String utmMedium;

    @Column(name = "utm_campaign", updatable = false)
    private String utmCampaign;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DistributionLink() {
    }

    private DistributionLink(Long formId, Channel channel, String code, String formSlug, Instant now) {
        this.formId = formId;
        this.channel = channel;
        this.code = code;
        this.utmSource = channel.value();
        this.utmMedium = UTM_MEDIUM;
        this.utmCampaign = formSlug;
        this.createdAt = now;
    }

    public static DistributionLink create(Long formId, Channel channel, String code, String formSlug, Instant now) {
        return new DistributionLink(formId, channel, code, formSlug, now);
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
