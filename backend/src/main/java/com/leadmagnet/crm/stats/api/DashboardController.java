package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.stats.application.DashboardService;
import com.leadmagnet.crm.stats.application.DashboardService.FormSheetCsv;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "성과 조회", description = "캠페인·채널·크리에이터별 방문, 방문자, 신청, 전환 방문자, 전환율 (ADR 0005, 0014)")
@RestController
public class DashboardController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final String PERIOD = "from, to는 yyyy-MM-dd(Asia/Seoul)이고 양 끝을 포함한다. 둘 다 없으면 서울 오늘까지 30일, "
        + "to만 없으면 오늘까지, from만 없으면 to까지 30일이다. 채운 기간은 366일까지이고 넘으면 422 validation_failed"
        + "(errors[].code range_too_long). 봇 방문은 빼고(ADR 0019) 전환은 ADR 0005를 따른다.";

    private final DashboardService dashboard;
    private final AuditService audit;

    public DashboardController(DashboardService dashboard, AuditService audit) {
        this.dashboard = dashboard;
        this.audit = audit;
    }

    @Operation(operationId = "getDashboard", summary = "유입 현황 대시보드",
        description = PERIOD + " totals는 전체 캠페인 합계(방문자는 캠페인을 가리지 않고 한 번, 전환은 같은 캠페인 안의 교집합). "
            + "daily는 기간의 모든 서울 날짜(0 포함)이고 일별 전환은 그날 방문하고 같은 날 같은 캠페인에 신청한 방문자다. "
            + "forms는 방문이나 신청이 있는 폼(최신 캠페인순, 같은 폼 안의 교집합), channels는 다섯 채널(같은 캠페인·채널 안의 교집합), "
            + "creators는 크리에이터 등록순 다음 직접·자체 링크(creator null), recentLeads는 최신 10건이고 가린 이름만 준다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "대시보드 지표")
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류, 366일 초과 (validation_failed)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/stats/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return DashboardResponse.of(dashboard.dashboard(from, to));
    }

    @Operation(operationId = "exportDashboardFormsCsv", summary = "대시보드 폼 시트 CSV 다운로드",
        description = PERIOD + " UTF-8 BOM으로 시작하는 RFC 4180 CSV. 열은 캠페인, 폼, 상태, 방문, 방문자, 신청, 전환 방문자, 전환율이고 "
            + "순서는 대시보드 forms와 같다. 개인정보는 없다. =, +, -, @, 탭, CR로 시작하는 값에는 작은따옴표를 붙인다. "
            + "다운로드는 감사 로그(stats.export, 기간·행 수)에 남기되 기록에 실패해도 내려준다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "CSV 파일",
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류, 366일 초과 (validation_failed)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/stats/dashboard.csv")
    public ResponseEntity<byte[]> exportForms(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        FormSheetCsv csv = dashboard.formSheetCsv(from, to);
        String periodFrom = csv.period().from().toString();
        String periodTo = csv.period().to().toString();
        // 집계 값만 있는 파일이라 기록은 best effort다(원문을 내주는 명단 CSV와 다르다, ADR 0016)
        audit.recordBestEffort(AuditActors.of(jwt, request), AuditEvent.statsExport(periodFrom, periodTo, csv.rows()));
        return ResponseEntity.ok()
            .contentType(TEXT_CSV)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("dashboard-forms-" + periodFrom + "-" + periodTo + ".csv").build().toString())
            .body(csv.content().getBytes(StandardCharsets.UTF_8));
    }
}
