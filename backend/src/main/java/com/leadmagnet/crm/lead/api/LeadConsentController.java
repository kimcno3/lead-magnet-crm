package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "명단", description = "캠페인별 신청 명단 (ADR 0008)")
@RestController
public class LeadConsentController {

    private final LeadConsentService consents;

    public LeadConsentController(LeadConsentService consents) {
        this.consents = consents;
    }

    @Operation(operationId = "getLeadConsent", summary = "신청자 동의 문구 증빙",
        description = "신청 시점 폼의 동의 문구 원문, 그 sha256(UTF-8 바이트, 소문자 hex), 동의 시각을 준다 (ADR 0020). "
            + "신청자 개인정보가 아니어서 원문 연락처 API와 따로 두고 감사 로그에 남기지 않는다. 파기된 신청도 증빙은 남아 있다. "
            + "V10 이전 신청은 그때 폼의 문구로 백필한 값이고, 백필하지 못했으면 null이다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "동의 증빙")
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "없는 신청 (not_found)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/leads/{id}/consent")
    public LeadConsentResponse consent(@PathVariable long id) {
        return LeadConsentResponse.from(consents.get(id));
    }
}
