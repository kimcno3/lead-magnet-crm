package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "HTML 템플릿", description = "AI가 만든 단일 HTML 폼 업로드와 조회 (ADR 0004)")
@RestController
@RequestMapping("/api/admin/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "템플릿 업로드", description = "multipart file(.html/.htm, UTF-8, 512KB 이하)과 선택 name. 폼 계약을 검사해 필드를 추출한다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "등록됨. Location에 상세 경로")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "413", description = "template_too_large 또는 payload_too_large")
    @ApiResponse(responseCode = "422", description = "template_empty, template_invalid_extension, template_not_utf8, "
        + "template_form_count_invalid, template_forbidden_field, template_field_invalid, "
        + "template_contact_field_missing, validation_failed")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateResponse> upload(@RequestPart("file") MultipartFile file,
            @RequestParam(name = "name", required = false) String name,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        HtmlTemplate created = templateService.upload(file.getOriginalFilename(), file.getBytes(), name,
            Long.parseLong(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/admin/templates/" + created.getId()))
            .body(TemplateResponse.from(created));
    }

    @Operation(summary = "템플릿 목록", description = "최신순", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "목록")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @GetMapping
    public List<TemplateSummaryResponse> list() {
        return templateService.list().stream().map(TemplateSummaryResponse::from).toList();
    }

    @Operation(summary = "템플릿 상세", description = "원문 content와 추출한 fields 포함", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @GetMapping("/{id}")
    public TemplateDetailResponse get(@PathVariable long id) {
        return TemplateDetailResponse.from(templateService.get(id));
    }
}
