package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.form.application.TemplateVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 템플릿 새 버전 업로드(ADR 0018). draft 폼의 스냅샷을 다시 만들므로 form 패키지에 둔다(template은 form을 모른다).
 * 경로는 템플릿 API와 같다.
 */
@Tag(name = "HTML 템플릿", description = "AI가 만든 단일 HTML 폼 업로드와 조회 (ADR 0004)")
@RestController
@RequestMapping("/api/admin/templates")
public class TemplateVersionController {

    private final TemplateVersionService versionService;

    public TemplateVersionController(TemplateVersionService versionService) {
        this.versionService = versionService;
    }

    @Operation(operationId = "uploadTemplateVersion", summary = "템플릿 새 버전 업로드",
        description = "업로드와 같은 파일 검사(multipart file, .html/.htm, UTF-8, 512KB 이하, 폼 계약)를 통과하면 내용·sha256·필드·크기·검사 결과를 바꾸고 version을 올린다. "
            + "이 템플릿을 쓰는 draft 폼은 새 필드로 다시 스냅샷한다(운영자가 바꾼 label·required는 같은 이름이면 유지). "
            + "게시·마감된 폼은 게시 시점 스냅샷을 쓰므로 영향이 없다. 감사 로그(template.version.upload)에 버전과 영향받은 draft 수가 남고, 기록에 실패하면 업로드도 롤백된다 (ADR 0016). 응답 affectedDraftForms는 다시 스냅샷한 draft 폼 수. 이름은 바뀌지 않는다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "새 버전 적용됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @ApiResponse(responseCode = "413", description = "template_too_large 또는 payload_too_large")
    @ApiResponse(responseCode = "422", description = "업로드와 같은 계약 오류(template_*). 템플릿은 바뀌지 않는다")
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateVersionResponse uploadVersion(@PathVariable long id, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) throws IOException {
        return TemplateVersionResponse.from(versionService.upload(id, file.getOriginalFilename(), file.getBytes(),
            AuditActors.of(jwt, httpRequest)));
    }
}
