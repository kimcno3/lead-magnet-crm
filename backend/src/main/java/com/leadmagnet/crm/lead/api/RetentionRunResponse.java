package com.leadmagnet.crm.lead.api;

/**
 * 파기 실행 결과. dryRun이면 anonymized는 지금 실행하면 익명화될 건수다(아무것도 바꾸지 않음).
 * interrupted면 다른 실행이 배치를 처리 중이어서 이번 실행이 도중에 멈췄다(남은 대상은 그 실행이 처리한다).
 */
public record RetentionRunResponse(long anonymized, boolean dryRun, boolean interrupted) {
}
