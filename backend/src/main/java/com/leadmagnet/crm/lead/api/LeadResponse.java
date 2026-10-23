package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadQueryService.LeadView;
import java.time.Instant;
import java.util.Map;

/**
 * 명단 항목. name·phone·email은 가린 값이다(ADR 0015). contactUnavailable이면 연락처를 복호화하지 못해 셋 다 null이다.
 * anonymizedAt이 있으면 보존 기간이 지나 파기된 신청이다(연락처 null, payload는 키만, ADR 0020).
 * visitorId, consentAt 같은 내부 정보는 내보내지 않는다(동의 증빙은 GET /api/admin/leads/{id}/consent).
 */
public record LeadResponse(Long id, Long formId, String formTitle, String channel, String name, String phone,
        String email, boolean contactUnavailable, Map<String, Object> payload, Instant createdAt,
        Instant anonymizedAt) {

    public static LeadResponse from(LeadView view) {
        return new LeadResponse(view.id(), view.formId(), view.formTitle(), view.channel(), view.name(), view.phone(),
            view.email(), view.contactUnavailable(), view.payload(), view.createdAt(), view.anonymizedAt());
    }
}
