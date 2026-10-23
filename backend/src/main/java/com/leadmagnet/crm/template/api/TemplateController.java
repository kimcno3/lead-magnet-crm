package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.form.application.TemplateVersionService;
import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final TemplateVersionService versionService;

    public TemplateController(TemplateService templateService, TemplateVersionService versionService) {
        this.templateService = templateService;
        this.versionService = versionService;
    }

    @Operation(operationId = "uploadTemplate", summary = "템플릿 업로드", description = "multipart file(.html/.htm, UTF-8, 512KB 이하)과 선택 name. 폼 계약을 검사해 필드를 추출하고, 응답 inspection에 경고(외부 리소스 등)를 담는다. 경고는 업로드를 막지 않는다.",
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
            .body(TemplateResponse.from(created, templateService.inspectionOf(created)));
    }

    @Operation(operationId = "inspectTemplate", summary = "템플릿 검사만 하기",
        description = "업로드와 같은 파일 검사를 하고 추출될 필드와 경고(inspection)만 돌려준다. 아무것도 저장하지 않는다. "
            + "경고는 업로드를 막지 않고, 계약 오류는 업로드와 같은 오류 코드로 답한다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "검사 리포트(저장 안 됨)")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "413", description = "template_too_large 또는 payload_too_large")
    @ApiResponse(responseCode = "422", description = "업로드와 같은 계약 오류(template_*)")
    @PostMapping(path = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateInspectionResponse inspect(@RequestPart("file") MultipartFile file,
            @RequestParam(name = "name", required = false) String name) throws IOException {
        TemplateService.Inspected checked = templateService.inspect(file.getOriginalFilename(), file.getBytes(), name);
        return new TemplateInspectionResponse(checked.name(),
            checked.content().getBytes(StandardCharsets.UTF_8).length, checked.fields(),
            checked.inspection());
    }

    @Operation(operationId = "listTemplates", summary = "템플릿 목록", description = "최신순", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "목록")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @GetMapping
    public List<TemplateSummaryResponse> list() {
        return templateService.list().stream().map(TemplateSummaryResponse::from).toList();
    }

    @Operation(operationId = "getTemplate", summary = "템플릿 상세", description = "원문 content, 추출한 fields, 검사 경고 inspection 포함. 검사 결과가 없는 기존 템플릿은 읽을 때 계산한다.", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @GetMapping("/{id}")
    public TemplateDetailResponse get(@PathVariable long id) {
        HtmlTemplate template = templateService.get(id);
        return TemplateDetailResponse.from(template, templateService.inspectionOf(template));
    }

    @Operation(operationId = "uploadTemplateVersion", summary = "템플릿 새 버전 업로드",
        description = "업로드와 같은 파일 검사(multipart file, .html/.htm, UTF-8, 512KB 이하, 폼 계약)를 통과하면 내용·sha256·필드·크기·검사 결과를 바꾸고 version을 올린다. "
            + "이 템플릿을 쓰는 draft 폼은 새 필드로 다시 스냅샷한다(운영자가 바꾼 label·required는 같은 이름이면 유지). "
            + "게시·마감된 폼은 게시 시점 스냅샷을 쓰므로 영향이 없다. 응답 affectedDraftForms는 다시 스냅샷한 draft 폼 수. 이름은 바뀌지 않는다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "새 버전 적용됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @ApiResponse(responseCode = "413", description = "template_too_large 또는 payload_too_large")
    @ApiResponse(responseCode = "422", description = "업로드와 같은 계약 오류(template_*). 템플릿은 바뀌지 않는다")
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateVersionResponse uploadVersion(@PathVariable long id, @RequestPart("file") MultipartFile file)
            throws IOException {
        return TemplateVersionResponse.from(versionService.upload(id, file.getOriginalFilename(), file.getBytes()));
    }
}
