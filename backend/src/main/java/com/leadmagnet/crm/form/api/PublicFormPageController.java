package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.application.PublicFormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import com.leadmagnet.crm.tracking.application.VisitRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 폼 페이지 (ADR 0002). 응답은 동적 값이 없는 고정 셸이고, 폼 내용은 래퍼 스크립트가 공개 API로 받아
 * 샌드박스 iframe에 넣는다. 200 응답마다 방문 한 행을 남긴다(ADR 0006). 브라우저 페이지라 404·410도 HTML 안내로 답한다.
 */
@Tag(name = "공개 폼", description = "public 출처(:8081) 전용. 래퍼 스크립트가 호출한다 (ADR 0002)")
@RestController
public class PublicFormPageController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final PublicFormService publicForms;
    private final VisitRecorder visits;
    private final VisitorIdCookie visitorCookie;

    public PublicFormPageController(PublicFormService publicForms, VisitRecorder visits,
            VisitorIdCookie visitorCookie) {
        this.publicForms = publicForms;
        this.visits = visits;
        this.visitorCookie = visitorCookie;
    }

    @Operation(summary = "공개 폼 페이지", description = "래퍼 셸 HTML. 방문 1건 기록, 방문자 쿠키 lm_vid 발급. "
        + "lm이 이 폼의 링크 code면 그 채널로, 아니면 direct로 귀속한다.")
    @ApiResponse(responseCode = "200", description = "래퍼 셸")
    @ApiResponse(responseCode = "404", description = "없는 slug 또는 draft (HTML 안내)")
    @ApiResponse(responseCode = "410", description = "closed (HTML 안내)")
    @GetMapping("/f/{slug}")
    public ResponseEntity<String> page(@PathVariable String slug, @RequestParam(name = "lm", required = false) String lm,
            HttpServletRequest request, HttpServletResponse response) {
        Form form = publicForms.getOpen(slug);
        UUID visitorId = visitorCookie.resolve(request, response);
        visits.record(form.getId(), lm, visitorId, request.getHeader(HttpHeaders.REFERER),
            request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok().contentType(HTML).body(WrapperPages.SHELL);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<String> notice(DomainException e) {
        ErrorCode code = e.getErrorCode();
        String body = code == ErrorCode.GONE ? WrapperPages.GONE : WrapperPages.NOT_FOUND;
        return ResponseEntity.status(code.status()).contentType(HTML).body(body);
    }
}
