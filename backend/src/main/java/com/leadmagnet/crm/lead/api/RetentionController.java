package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.common.web.AuditActors;
import com.leadmagnet.crm.lead.application.LeadRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "보존 기간", description = "보존 기간이 지난 신청 익명화 (ADR 0020)")
@RestController
public class RetentionController {

    private final LeadRetentionService retention;

    public RetentionController(LeadRetentionService retention) {
        this.retention = retention;
    }

    @Operation(operationId = "runRetention", summary = "보존 기간 파기 수동 실행",
        description = "매일 03:00(Asia/Seoul) 스케줄러와 같은 작업을 지금 실행한다. 캠페인의 retentionDays × 24시간이 지난 신청의 "
            + "이름·연락처(암호문·해시)를 지우고 payload는 키만 남긴다. 행과 지표에 쓰는 값은 남는다. "
            + "실행은 감사 로그(retention.anonymize, 건수)에 남는다. 다른 인스턴스가 배치를 처리 중이면 그 실행에 맡기고 "
            + "이번 실행이 처리한 건수만 준다. dryRun=true면 아무것도 바꾸지 않고 대상 건수만 준다(기록 없음).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "익명화한(dryRun이면 대상) 신청 수")
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/api/admin/retention/run")
    public RetentionRunResponse run(@RequestParam(defaultValue = "false") boolean dryRun,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (dryRun) {
            return new RetentionRunResponse(retention.countDue(), true);
        }
        return new RetentionRunResponse(
            retention.run(LeadRetentionService.TRIGGER_MANUAL, AuditActors.of(jwt, request)), false);
    }
}
