package com.leadmagnet.crm.audit.domain;

import com.leadmagnet.crm.common.web.AuditActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 감사 기록 한 줄 (ADR 0016). 쓰기 전용이라 만든 뒤 바꾸는 메서드가 없다. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", updatable = false)
    private Long operatorId;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(name = "campaign_id", updatable = false)
    private Long campaignId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(updatable = false, length = AuditActor.MAX_IP_LENGTH)
    private String ip;

    @Column(name = "user_agent", updatable = false, length = AuditActor.MAX_USER_AGENT_LENGTH)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    private AuditLog(AuditActor actor, AuditEvent event, Instant now) {
        this.operatorId = actor.operatorId();
        this.action = event.action().value();
        this.targetType = event.targetType();
        this.targetId = event.targetId();
        this.campaignId = event.campaignId();
        this.detail = event.detail();
        this.ip = actor.ip();
        this.userAgent = actor.userAgent();
        this.createdAt = now;
    }

    public static AuditLog record(AuditActor actor, AuditEvent event, Instant now) {
        return new AuditLog(actor, event, now);
    }

    public Long getId() {
        return id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
