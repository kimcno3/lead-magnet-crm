package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadQueryService.LeadView;
import java.time.Instant;
import java.util.Map;

/**
 * 명단 항목. name·phone·email은 가린 값이다(ADR 0015). contactUnavailable이면 연락처를 복호화하지 못해 셋 다 null이다.
 * visitorId, consentAt 같은 내부 정보는 내보내지 않는다.
 */
public record LeadResponse(Long id, Long formId, String formTitle, String channel, String name, String phone,
        String email, boolean contactUnavailable, Map<String, Object> payload, Instant createdAt) {

    public static LeadResponse from(LeadView view) {
        return new LeadResponse(view.id(), view.formId(), view.formTitle(), view.channel(), view.name(), view.phone(),
            view.email(), view.contactUnavailable(), view.payload(), view.createdAt());
    }
}
