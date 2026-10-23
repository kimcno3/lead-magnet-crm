package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.common.web.AuditActors;
import com.leadmagnet.crm.lead.application.LeadContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "명단", description = "캠페인별 신청 명단 (ADR 0008)")
@RestController
public class LeadContactController {

    private final LeadContactService contacts;

    public LeadContactController(LeadContactService contacts) {
        this.contacts = contacts;
    }

    @Operation(operationId = "getLeadContact", summary = "신청자 원문 연락처",
        description = "명단은 가린 값만 준다. 원문 이름·전화번호(숫자만)·이메일은 이 엔드포인트로 한 건씩 본다 (ADR 0015). "
            + "응답은 캐시하지 않는다(Cache-Control: no-store). 열람은 감사 로그(lead.contact.view)에 남고, "
            + "기록에 실패하면 원문을 주지 않고 500이다(ADR 0016).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "원문 연락처")
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "없는 신청 (not_found)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "410", description = "보존 기간이 지나 파기된 신청 (lead_anonymized, ADR 0020)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/admin/leads/{id}/contact")
    public ResponseEntity<LeadContactResponse> contact(@PathVariable long id, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(LeadContactResponse.from(contacts.reveal(id, AuditActors.of(jwt, request))));
    }
}
