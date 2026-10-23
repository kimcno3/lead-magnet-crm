package com.leadmagnet.crm.link.api;

import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.application.LinkService.LinkDetail;
import com.leadmagnet.crm.link.config.LinkProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "배포 링크", description = "폼의 채널·크리에이터별 배포 링크 (ADR 0006, 0014)")
@RestController
@RequestMapping("/api/admin/forms/{formId}/links")
public class LinkController {

    private final LinkService linkService;
    private final LinkProperties properties;

    public LinkController(LinkService linkService, LinkProperties properties) {
        this.linkService = linkService;
        this.properties = properties;
    }

    @Operation(operationId = "createLink", summary = "배포 링크 생성",
        description = "channel은 instagram, x, youtube, threads. creatorId는 선택(없으면 크리에이터 없는 링크). "
            + "폼 × 채널 × 크리에이터(없음 포함)당 하나, draft·published 폼과 보관되지 않은 크리에이터로만 만든다.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "폼 또는 크리에이터 없음 (not_found)")
    @ApiResponse(responseCode = "409", description = "link_exists, form_closed, creator_archived")
    @ApiResponse(responseCode = "422", description = "direct·모르는 채널 (validation_failed)")
    @PostMapping
    public ResponseEntity<LinkResponse> create(@PathVariable long formId, @RequestBody CreateLinkRequest request) {
        LinkDetail created = linkService.create(formId, request.channel(), request.creatorId());
        return ResponseEntity.created(URI.create("/api/admin/forms/" + formId + "/links"))
            .body(LinkResponse.from(created, properties.baseUrl()));
    }

    @Operation(operationId = "listLinks", summary = "배포 링크 목록", description = "크리에이터 없는 링크 먼저, 그다음 크리에이터 등록순. 같은 크리에이터 안에서는 instagram, x, youtube, threads 순서.",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "목록")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "404", description = "폼 없음 (not_found)")
    @GetMapping
    public List<LinkResponse> list(@PathVariable long formId) {
        return linkService.list(formId).stream().map(l -> LinkResponse.from(l, properties.baseUrl())).toList();
    }
}
