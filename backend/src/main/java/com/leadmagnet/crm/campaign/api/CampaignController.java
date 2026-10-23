package com.leadmagnet.crm.campaign.api;

import com.leadmagnet.crm.campaign.application.CampaignService;
import com.leadmagnet.crm.campaign.application.CampaignService.CampaignDetail;
import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.common.web.AuditActors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캠페인", description = "캠페인 생성·조회·수정·보관 (ADR 0013)")
@RestController
@RequestMapping("/api/admin/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @Operation(operationId = "createCampaign", summary = "캠페인 생성",
        description = "retentionDays는 신청 보존 기간(일, 1~3650, 기본 365). 지나면 매일 03:00(KST) 익명화한다 (ADR 0020).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "생성됨. Location에 상세 경로")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "422", description = "validation_failed")
    @PostMapping
    public ResponseEntity<CampaignResponse> create(@RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Campaign created = campaignService.create(request.name(), request.description(), request.startsAt(),
            request.endsAt(), request.retentionDays(), Long.parseLong(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/admin/campaigns/" + created.getId()))
            .body(CampaignResponse.from(created, List.of()));
    }

    @Operation(operationId = "listCampaigns", summary = "캠페인 목록", description = "최신순. formCount는 상태와 무관하게 센다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "목록")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @GetMapping
    public List<CampaignSummaryResponse> list() {
        return campaignService.list().stream().map(CampaignSummaryResponse::from).toList();
    }

    @Operation(operationId = "getCampaign", summary = "캠페인 상세", description = "소속 폼 요약 포함", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @GetMapping("/{id}")
    public CampaignResponse get(@PathVariable long id) {
        return toResponse(campaignService.get(id));
    }

    @Operation(operationId = "updateCampaign", summary = "캠페인 수정·보관", description = "없는 필드는 그대로 둔다. status는 \"archived\"만 받는다. retentionDays는 1~3650이고, 바뀌면 감사 로그(campaign.retention.update, 이전·새 값)에 남는다 (ADR 0020).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @ApiResponse(responseCode = "409", description = "campaign_already_archived")
    @ApiResponse(responseCode = "422", description = "validation_failed")
    @PatchMapping("/{id}")
    public CampaignResponse update(@PathVariable long id, @RequestBody UpdateCampaignRequest request,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return toResponse(campaignService.update(id, request.name(), request.description(), request.startsAt(),
            request.endsAt(), request.retentionDays(), request.status(), AuditActors.of(jwt, httpRequest)));
    }

    private static CampaignResponse toResponse(CampaignDetail detail) {
        return CampaignResponse.from(detail.campaign(), detail.forms());
    }
}
