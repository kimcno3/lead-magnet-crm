package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.lead.application.LeadQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "명단", description = "캠페인별 신청 명단 (ADR 0008)")
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/leads")
public class LeadController {

    private final LeadQueryService leads;
    private final AuditService audit;

    public LeadController(LeadQueryService leads, AuditService audit) {
        this.leads = leads;
        this.audit = audit;
    }

    @Operation(operationId = "listLeads", summary = "캠페인 명단", description = "최신순. page는 0부터, size는 1~100(기본 20). "
        + "channel은 instagram, x, youtube, threads, direct. 조회는 감사 로그(lead.list)에 남는다(ADR 0016).", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "명단 한 페이지")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "없는 캠페인 (not_found)")
    @ApiResponse(responseCode = "422", description = "잘못된 page·size·channel (validation_failed)")
    @GetMapping
    public LeadPageResponse list(@PathVariable long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + LeadQueryService.DEFAULT_SIZE) int size,
            @RequestParam(required = false) Long formId,
            @RequestParam(required = false) String channel,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        LeadPageResponse response = LeadPageResponse.from(leads.list(campaignId, page, size, formId, channel));
        // 조회 트랜잭션이 끝난 뒤 기록한다. 기록이 실패해도 응답한다(ADR 0016).
        audit.recordBestEffort(AuditActors.of(jwt, request),
            AuditEvent.leadList(campaignId, formId, channel, page, size));
        return response;
    }
}
