package com.leadmagnet.crm.lead.api;

/** 파기 실행 결과. dryRun이면 anonymized는 지금 실행하면 익명화될 건수다(아무것도 바꾸지 않음). */
public record RetentionRunResponse(long anonymized, boolean dryRun) {
}
