package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.audit.api.AuditActors;
import com.leadmagnet.crm.lead.application.PiiReencryptionService;
import com.leadmagnet.crm.lead.application.PiiReencryptionService.ReencryptRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개인정보 키", description = "개인정보 암호화 키 교체 (ADR 0022)")
@RestController
public class PiiReencryptController {

    private final PiiReencryptionService reencryption;

    public PiiReencryptController(PiiReencryptionService reencryption) {
        this.reencryption = reencryption;
    }

    @Operation(operationId = "reencryptPii", summary = "개인정보 재암호화 실행",
        description = "LEAD_ENC_KEY를 새 키(LEAD_ENC_KEY_ID)로 바꾸고 이전 키를 LEAD_ENC_PREVIOUS_KEYS에 둔 채 기동한 뒤 부른다. "
            + "현재 키가 아닌 키로 만든 신청 암호문(이름·전화번호·이메일)을 배치마다 현재 키로 다시 암호화하고, 신청이 모두 끝나면 "
            + "키 확인 행도 바꾼다. 파기된 신청은 건너뛴다. 응답의 remaining이 0이면 LEAD_ENC_PREVIOUS_KEYS를 지우고 다시 기동해도 "
            + "된다. 실행은 감사 로그(pii.reencrypt, 건수만)에 남는다. 다른 인스턴스가 처리 중이면 interrupted=true. 배치가 실패하면 "
            + "그때까지의 건수를 completed=false로 기록한 뒤 500이다(앞 배치는 그대로, 다시 실행하면 이어서 한다). "
            + "dryRun=true면 아무것도 바꾸지 않고 남은 건수만 준다(기록 없음).",
        security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "재암호화 결과와 남은 건수")
    @ApiResponse(responseCode = "500", description = "배치 실패 (internal_error). 그때까지의 건수는 감사 로그에 completed=false로 남는다",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/api/admin/pii/reencrypt")
    public PiiReencryptResponse reencrypt(@RequestParam(defaultValue = "false") boolean dryRun,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (dryRun) {
            return new PiiReencryptResponse(0, 0, reencryption.remaining(), reencryption.currentKeyId(), true, false);
        }
        ReencryptRun run = reencryption.run(AuditActors.of(jwt, request));
        return new PiiReencryptResponse(run.reencrypted(), run.unreadable(), run.remaining(), reencryption.currentKeyId(),
            false, run.interrupted());
    }
}
