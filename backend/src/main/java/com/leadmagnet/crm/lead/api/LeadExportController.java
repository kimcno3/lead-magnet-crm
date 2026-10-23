package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.lead.application.LeadExportService;
import com.leadmagnet.crm.lead.application.LeadExportService.LeadExport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "명단", description = "캠페인별 신청 명단 (ADR 0008)")
@RestController
public class LeadExportController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final LeadExportService exports;
    private final Clock clock;

    public LeadExportController(LeadExportService exports, Clock clock) {
        this.exports = exports;
        this.clock = clock;
    }

    @Operation(operationId = "exportLeadsCsv", summary = "명단 CSV 다운로드",
        description = "UTF-8 BOM으로 시작하는 RFC 4180 CSV. 열은 신청일(Asia/Seoul), 이름, 전화번호, 이메일, 채널, 크리에이터, 폼, "
            + "추가 항목(payload JSON), 파기이고 신청 순이다. 보존 기간이 지나 파기된 신청은 연락처·추가 항목 칸이 비고 파기 칸이 \"파기됨\"이다(ADR 0020). 기본은 가린 값, reveal=true면 원문(전화번호는 숫자만)이다. "
            + "=, +, -, @, 탭, CR로 시작하는 값에는 작은따옴표를 붙인다. 복호화하지 못한 행은 연락처 칸에 \"복호화 불가\". "
            + "다운로드는 감사 로그(lead.export, 원문은 lead.export.reveal)에 건수와 함께 남고, 기록에 실패하면 내용을 주지 않고 500이다(ADR 0016).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "CSV 파일",
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "없는 캠페인 (not_found)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "잘못된 channel (validation_failed)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/campaigns/{campaignId}/leads.csv")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable long campaignId,
            @RequestParam(required = false) Long formId,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "false") boolean reveal,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        LeadExport export = exports.start(campaignId, formId, channel, reveal, AuditActors.of(jwt, request));
        return ResponseEntity.ok()
            .contentType(TEXT_CSV)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(export.fileName(clock.instant())).build().toString())
            .body(out -> exports.write(export, out));
    }
}
