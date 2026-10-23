package com.leadmagnet.crm.creator.api;

import com.leadmagnet.crm.common.web.AuditActors;
import com.leadmagnet.crm.creator.application.CreatorService;
import com.leadmagnet.crm.creator.domain.Creator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크리에이터", description = "수익 배분 협업 크리에이터 등록·조회·수정·보관 (ADR 0014)")
@RestController
@RequestMapping("/api/admin/creators")
public class CreatorController {

    private final CreatorService creatorService;

    public CreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }

    @Operation(operationId = "createCreator", summary = "크리에이터 등록", description = "name 1~100자, handle 100자·memo 2000자 이하(선택).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "생성됨. Location에 상세 경로")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "422", description = "validation_failed")
    @PostMapping
    public ResponseEntity<CreatorResponse> create(@RequestBody CreateCreatorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Creator created = creatorService.create(request.name(), request.handle(), request.memo(),
            Long.parseLong(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/admin/creators/" + created.getId()))
            .body(CreatorResponse.from(created));
    }

    @Operation(operationId = "listCreators", summary = "크리에이터 목록", description = "최신순. 보관된 크리에이터도 포함한다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "목록")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @GetMapping
    public List<CreatorResponse> list() {
        return creatorService.list().stream().map(CreatorResponse::from).toList();
    }

    @Operation(operationId = "getCreator", summary = "크리에이터 상세", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "상세")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @GetMapping("/{id}")
    public CreatorResponse get(@PathVariable long id) {
        return CreatorResponse.from(creatorService.get(id));
    }

    @Operation(operationId = "updateCreator", summary = "크리에이터 수정·보관",
        description = "없는 필드는 그대로 둔다. handle·memo에 빈 문자열을 주면 지운다. status는 \"archived\"만 받는다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "not_found")
    @ApiResponse(responseCode = "409", description = "creator_already_archived")
    @ApiResponse(responseCode = "422", description = "validation_failed")
    @PatchMapping("/{id}")
    public CreatorResponse update(@PathVariable long id, @RequestBody UpdateCreatorRequest request,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return CreatorResponse.from(creatorService.update(id, request.name(), request.handle(), request.memo(),
            request.status(), AuditActors.of(jwt, httpRequest)));
    }
}
