package com.leadmagnet.crm.stats.api;

import com.leadmagnet.crm.stats.application.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "성과 조회", description = "캠페인·채널별 방문, 방문자, 신청, 전환 방문자, 전환율 (ADR 0005)")
@RestController
@RequestMapping("/api/admin")
public class StatsController {

    private static final String PERIOD = "from, to는 yyyy-MM-dd(Asia/Seoul)이고 둘 다 선택이다. "
        + "from 00:00 이상, to 다음날 00:00 미만. conversionRate는 소수 넷째 자리 반올림.";

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @Operation(operationId = "getCampaignStats", summary = "캠페인 지표", description = PERIOD, security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "캠페인 전체 지표")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "없는 캠페인 (not_found)")
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류 (validation_failed)")
    @GetMapping("/campaigns/{campaignId}/stats")
    public CampaignStatsResponse campaign(@PathVariable long campaignId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return CampaignStatsResponse.of(campaignId, from, to, stats.campaign(campaignId, from, to));
    }

    @Operation(operationId = "getCampaignChannelStats", summary = "캠페인 채널별 지표", description = PERIOD + " 다섯 채널을 항상 0 포함해 돌려준다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "instagram, x, youtube, threads, direct 순서")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "없는 캠페인 (not_found)")
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류 (validation_failed)")
    @GetMapping("/campaigns/{campaignId}/stats/channels")
    public ChannelStatsResponse channels(@PathVariable long campaignId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ChannelStatsResponse.of(campaignId, from, to, stats.channels(campaignId, from, to));
    }

    @Operation(operationId = "listCampaignStats", summary = "캠페인별 지표 목록", description = PERIOD + " 최신 캠페인순, 데이터가 없는 캠페인도 포함.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "캠페인별 지표")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류 (validation_failed)")
    @GetMapping("/stats/campaigns")
    public CampaignListStatsResponse campaigns(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return CampaignListStatsResponse.of(from, to, stats.campaigns(from, to));
    }
}
