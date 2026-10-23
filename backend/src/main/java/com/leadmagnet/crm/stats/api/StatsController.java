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

@Tag(name = "성과 조회", description = "캠페인·채널·크리에이터별 방문, 방문자, 신청, 전환 방문자, 전환율 (ADR 0005, 0014)")
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

    @Operation(operationId = "getCampaignCreatorStats", summary = "캠페인 크리에이터별 지표",
        description = PERIOD + " 이 캠페인에 링크가 있는 크리에이터를 등록순으로(데이터가 없어도 0), 마지막에 creator가 null인 "
            + "\"직접·자체 링크\"(크리에이터 없는 링크 + direct) 행. 전환은 같은 크리에이터 안의 교집합이고, channels는 같은 "
            + "크리에이터·같은 채널 안의 교집합이다(크리에이터 행은 링크 채널 네 개, 직접·자체 링크 행은 direct까지 다섯 개).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "크리에이터별 지표")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "없는 캠페인 (not_found)")
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류 (validation_failed)")
    @GetMapping("/campaigns/{campaignId}/stats/creators")
    public CreatorStatsResponse creators(@PathVariable long campaignId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return CreatorStatsResponse.of(campaignId, from, to, stats.creators(campaignId, from, to));
    }

    @Operation(operationId = "getCreatorCampaignStats", summary = "크리에이터의 캠페인별 지표",
        description = PERIOD + " 이 크리에이터의 링크가 있는 캠페인만 최신순. 그 크리에이터 링크로 귀속된 방문·신청만 세고, "
            + "전환은 같은 캠페인 안의 교집합이다(캠페인 행끼리 방문자를 합산하지 않는다).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "캠페인별 지표")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "없는 크리에이터 (not_found)")
    @ApiResponse(responseCode = "422", description = "날짜 형식·범위 오류 (validation_failed)")
    @GetMapping("/creators/{creatorId}/stats")
    public CreatorCampaignStatsResponse creatorCampaigns(@PathVariable long creatorId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return CreatorCampaignStatsResponse.of(from, to, stats.creatorCampaigns(creatorId, from, to));
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
