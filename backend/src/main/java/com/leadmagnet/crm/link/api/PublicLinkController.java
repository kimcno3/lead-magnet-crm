package com.leadmagnet.crm.link.api;

import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.application.LinkService.RedirectTarget;
import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 단축 URL. 방문을 기록하지 않고 리다이렉트만 한다. 방문은 /f/{slug} 페이지 로드에서 lm으로
 * 채널을 정해 한 번만 기록한다(ADR 0006, 이중 집계 방지). 방문자 쿠키 lm_vid가 없으면 여기서도 발급한다.
 */
@Tag(name = "공개 링크", description = "단축 URL 리다이렉트")
@RestController
public class PublicLinkController {

    private final LinkService linkService;
    private final VisitorIdCookie visitorCookie;

    public PublicLinkController(LinkService linkService, VisitorIdCookie visitorCookie) {
        this.linkService = linkService;
        this.visitorCookie = visitorCookie;
    }

    @Operation(summary = "단축 URL 리다이렉트", description = "302 Location: /f/{slug}?lm={code}")
    @ApiResponse(responseCode = "302", description = "공개 폼으로 이동")
    @ApiResponse(responseCode = "404", description = "없는 code 또는 draft 폼")
    @ApiResponse(responseCode = "410", description = "closed 폼 (gone)")
    @GetMapping("/l/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request,
            HttpServletResponse response) {
        RedirectTarget target = linkService.resolveRedirect(code);
        visitorCookie.resolve(request, response);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, target.location())
            .build();
    }
}
