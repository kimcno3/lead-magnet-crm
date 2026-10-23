package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.application.AuditService.AuditLogView;
import java.time.Instant;
import java.util.Map;

/** 감사 기록 한 줄. operatorId·operatorEmail은 로그인 실패처럼 운영자를 특정하지 않은 기록이면 null이다. */
public record AuditLogResponse(Long id, Long operatorId, String operatorEmail, String action, String targetType,
        Long targetId, Long campaignId, Map<String, Object> detail, String ip, String userAgent, Instant createdAt) {

    public static AuditLogResponse from(AuditLogView v, String operatorEmail) {
        return new AuditLogResponse(v.id(), v.operatorId(), operatorEmail, v.action(), v.targetType(), v.targetId(),
            v.campaignId(), v.detail(), v.ip(), v.userAgent(), v.createdAt());
    }
}
