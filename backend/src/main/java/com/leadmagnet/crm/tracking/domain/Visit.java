package com.leadmagnet.crm.tracking.domain;

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
import java.util.UUID;

/**
 * 공개 폼 페이지 로드 1회 (ADR 0006: 방문 = /f/{slug} 200 응답). 만든 뒤에는 바꾸지 않는다.
 * referrer·user_agent는 요청 헤더 그대로라 길이를 믿을 수 없어 {@value #MAX_HEADER_LENGTH}자로 자른다.
 */
@Entity
@Table(name = "visits")
public class Visit {

    static final int MAX_HEADER_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false, updatable = false)
    private Long formId;

    @Column(name = "link_id", updatable = false)
    private Long linkId;

    @Convert(converter = AttributionChannelConverter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private AttributionChannel channel;

    @Column(name = "visitor_id", nullable = false, updatable = false)
    private UUID visitorId;

    @Column(updatable = false, columnDefinition = "text")
    private String referrer;

    @Column(name = "user_agent", updatable = false, columnDefinition = "text")
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected Visit() {
    }

    private Visit(Long formId, Long linkId, AttributionChannel channel, UUID visitorId, String referrer,
            String userAgent, Instant occurredAt) {
        this.formId = formId;
        this.linkId = linkId;
        this.channel = channel;
        this.visitorId = visitorId;
        this.referrer = clip(referrer);
        this.userAgent = clip(userAgent);
        this.occurredAt = occurredAt;
    }

    /** linkId와 channel은 귀속 결과(link 없음 ⇔ direct, DB CHECK)를 그대로 받는다. */
    public static Visit record(Long formId, Long linkId, AttributionChannel channel, UUID visitorId,
            String referrer, String userAgent, Instant occurredAt) {
        return new Visit(formId, linkId, channel, visitorId, referrer, userAgent, occurredAt);
    }

    private static String clip(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_HEADER_LENGTH ? value.substring(0, MAX_HEADER_LENGTH) : value;
    }

    public Long getId() {
        return id;
    }

    public Long getFormId() {
        return formId;
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

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
