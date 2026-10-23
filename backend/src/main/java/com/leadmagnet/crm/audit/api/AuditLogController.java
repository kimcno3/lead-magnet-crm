package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.application.AuditService.AuditSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "감사 로그", description = "명단 조회·원문 열람·CSV 다운로드·로그인 기록 (ADR 0016). 조회만 있고 수정·삭제는 없다.")
@RestController
public class AuditLogController {

    private final AuditService audit;

    public AuditLogController(AuditService audit) {
        this.audit = audit;
    }

    @Operation(operationId = "listAuditLogs", summary = "감사 로그",
        description = "최신순. action은 lead.list, lead.contact.view, lead.export, lead.export.reveal, "
            + "auth.login.success, auth.login.failure. from·to는 Asia/Seoul 날짜(yyyy-MM-dd, 양 끝 포함). "
            + "page는 0부터, size는 1~100(기본 20). 이 조회 자체는 기록하지 않는다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "감사 로그 한 페이지")
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "잘못된 action·page·size·기간 (validation_failed)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/audit-logs")
    public AuditLogPageResponse list(@RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AuditService.DEFAULT_SIZE) int size) {
        return AuditLogPageResponse.from(
            audit.search(new AuditSearch(action, operatorId, campaignId, from, to, page, size)));
    }
}
