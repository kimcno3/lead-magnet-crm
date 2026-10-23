package com.leadmagnet.crm.audit.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 무엇을 했는지(action과 대상). detail에는 필터·페이지·건수·가린 이메일만 넣는다. 신청자 원문 개인정보는 넣지 않는다(ADR 0016).
 * detail 값의 null은 키째 빼지 않고 그대로 둔다(필터를 쓰지 않았다는 뜻).
 */
public record AuditEvent(AuditAction action, String targetType, Long targetId, Long campaignId,
        Map<String, Object> detail) {

    public static final String TARGET_LEAD = "lead";
    public static final String TARGET_CAMPAIGN = "campaign";
    public static final String TARGET_OPERATOR = "operator";

    public AuditEvent {
        detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }
}
