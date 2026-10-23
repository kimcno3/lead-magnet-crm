package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadSubmissionService;
import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 폼 신청 접수 (ADR 0002, 0007). CSP의 connect-src 'self' 때문에 sandbox된 업로드 HTML이 래퍼를 거치지 않고
 * 이 API를 직접 부를 수 있으므로 서버가 경계다: JSON만 받고, 동의와 필드 검증은 래퍼와 무관하게 서버에서 한다.
 * Origin: null 거절과 본문 크기 제한은 본문을 읽기 전에 {@code SubmissionGuardFilter}가 한다. 방문자 쿠키는 읽기만 하고 발급하지 않는다.
 */
@Tag(name = "공개 폼", description = "public 출처(:8081) 전용. 래퍼 스크립트가 호출한다 (ADR 0002)")
@RestController
public class PublicSubmissionController {

    private final LeadSubmissionService submissions;
    private final VisitorIdCookie visitorCookie;

    public PublicSubmissionController(LeadSubmissionService submissions, VisitorIdCookie visitorCookie) {
        this.submissions = submissions;
        this.visitorCookie = visitorCookie;
    }

    @Operation(operationId = "submitForm", summary = "신청 제출", description = "Content-Type: application/json만 받는다. Origin: null(sandbox 프레임)은 거절한다. "
        + "name·phone·email 필드는 컬럼으로, 나머지는 payload로 저장한다. 같은 폼의 같은 전화번호·이메일은 409.")
    @ApiResponse(responseCode = "201", description = "접수됨 {status: received}")
    @ApiResponse(responseCode = "400", description = "malformed_request")
    @ApiResponse(responseCode = "403", description = "forbidden_origin")
    @ApiResponse(responseCode = "404", description = "없는 slug 또는 draft (not_found)")
    @ApiResponse(responseCode = "409", description = "duplicate_submission")
    @ApiResponse(responseCode = "410", description = "closed (gone)")
    @ApiResponse(responseCode = "415", description = "unsupported_media_type")
    @ApiResponse(responseCode = "422", description = "validation_failed. errors[].code: unknown_field, required, "
        + "invalid_format, invalid_option, invalid_value, too_long, consent_required, contact_required")
    @PostMapping(path = "/api/public/forms/{slug}/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse submit(@PathVariable String slug, @RequestBody SubmissionRequest request,
            HttpServletRequest http) {
        submissions.submit(slug, request.fields(), request.consented(), request.lm(),
            visitorCookie.read(http).orElse(null));
        return SubmissionResponse.received();
    }
}
