package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.application.FormService;
import com.leadmagnet.crm.form.domain.Form;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "폼", description = "캠페인의 공개 신청 폼 생성·수정·게시·마감 (ADR 0013)")
@RestController
@RequestMapping("/api/admin")
public class FormController {

    private final FormService formService;

    public FormController(FormService formService) {
        this.formService = formService;
    }

    @Operation(operationId = "createForm", summary = "폼 생성", description = "템플릿 fields를 스냅샷으로 복사한 draft 폼을 만든다. slug를 생략하면 8자 랜덤.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "생성됨. Location에 상세 경로")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "캠페인 또는 템플릿 없음 (not_found)")
    @ApiResponse(responseCode = "409", description = "campaign_archived, slug_taken")
    @ApiResponse(responseCode = "422", description = "validation_failed")
    @PostMapping("/campaigns/{campaignId}/forms")
    public ResponseEntity<FormResponse> create(@PathVariable long campaignId, @RequestBody CreateFormRequest request) {
        Form created = formService.create(campaignId, request.templateId(), request.title(), request.consentText(),
            request.slug());
        return ResponseEntity.created(URI.create("/api/admin/forms/" + created.getId())).body(FormResponse.from(created));
    }

    @Operation(operationId = "getForm", summary = "폼 상세", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @GetMapping("/forms/{id}")
    public FormResponse get(@PathVariable long id) {
        return FormResponse.from(formService.get(id));
    }

    @Operation(operationId = "updateForm", summary = "폼 수정·게시·마감",
        description = "없는 필드는 그대로 둔다. 내용(title, consentText, fieldConfig의 label·required)은 draft에서만 수정. "
            + "status는 published(draft→published) 또는 closed(published→closed).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @ApiResponse(responseCode = "409", description = "invalid_form_transition, form_not_editable")
    @ApiResponse(responseCode = "422", description = "validation_failed (필드 추가·삭제·이름 변경 포함)")
    @PatchMapping("/forms/{id}")
    public FormResponse update(@PathVariable long id, @RequestBody UpdateFormRequest request) {
        return FormResponse.from(formService.update(id, request.title(), request.consentText(),
            request.fieldConfig(), request.status()));
    }
}
