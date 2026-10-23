package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.application.FormTokenService;
import com.leadmagnet.crm.form.application.PublicFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공개 폼", description = "public 출처(:8081) 전용. 래퍼 스크립트가 호출한다 (ADR 0002)")
@RestController
public class PublicFormController {

    private final PublicFormService publicForms;
    private final FormTokenService formTokens;

    public PublicFormController(PublicFormService publicForms, FormTokenService formTokens) {
        this.publicForms = publicForms;
        this.formTokens = formTokens;
    }

    @Operation(operationId = "getPublicForm", summary = "공개 폼 조회", description = "published 폼의 제목, 동의 문구, 필드 설정, 템플릿 HTML 원문과 "
        + "작성 시간 토큰 ft(제출에 그대로 실어 보낸다, ADR 0019)")
    @ApiResponse(responseCode = "200", description = "공개 중")
    @ApiResponse(responseCode = "404", description = "없는 slug 또는 draft (not_found)")
    @ApiResponse(responseCode = "410", description = "closed (gone)")
    @GetMapping("/api/public/forms/{slug}")
    public PublicFormResponse get(@PathVariable String slug) {
        PublicFormService.OpenForm open = publicForms.getOpenWithHtml(slug);
        return PublicFormResponse.from(open, formTokens.issue(open.form().getSlug()));
    }
}
