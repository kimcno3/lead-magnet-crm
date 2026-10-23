package com.leadmagnet.crm.lead.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재암호화 결과 (ADR 0022). remaining이 0이면 이전 키로 남은 행(신청과 키 확인 행)이 없으므로 LEAD_ENC_PREVIOUS_KEYS를 지워도 된다.
 */
public record PiiReencryptResponse(
        @Schema(description = "이번 실행이 현재 키로 다시 암호화한 신청 수(dryRun이면 0)") long reencrypted,
        @Schema(description = "어느 키로도 풀지 못해 건너뛴 신청 수. 남은 건수에 남는다") long unreadable,
        @Schema(description = "이전 키로 남은 행 수(신청 + 키 확인 행). 0이면 LEAD_ENC_PREVIOUS_KEYS를 지워도 된다") long remaining,
        @Schema(description = "현재 암호화 키 id(LEAD_ENC_KEY_ID)") int currentKeyId,
        boolean dryRun,
        @Schema(description = "다른 실행이 처리 중이어서 이번 실행이 멈췄는지") boolean interrupted) {
}
