package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.application.AuditService.AuditLogPage;
import java.util.List;

public record AuditLogPageResponse(List<AuditLogResponse> items, int page, int size, long totalElements,
        int totalPages) {

    public static AuditLogPageResponse from(AuditLogPage page) {
        return new AuditLogPageResponse(page.items().stream().map(AuditLogResponse::from).toList(), page.page(),
            page.size(), page.totalElements(), page.totalPages());
    }
}
