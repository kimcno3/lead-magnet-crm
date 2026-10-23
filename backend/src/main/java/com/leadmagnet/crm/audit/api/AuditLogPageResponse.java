package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.application.AuditService.AuditLogPage;
import java.util.List;
import java.util.Map;

public record AuditLogPageResponse(List<AuditLogResponse> items, int page, int size, long totalElements,
        int totalPages) {

    /** emails: 운영자 id → 이메일. 없는 운영자는 이메일 null. */
    public static AuditLogPageResponse from(AuditLogPage page, Map<Long, String> emails) {
        return new AuditLogPageResponse(
            page.items().stream().map(v -> AuditLogResponse.from(v, emails.get(v.operatorId()))).toList(), page.page(),
            page.size(), page.totalElements(), page.totalPages());
    }
}
