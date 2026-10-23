package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadQueryService.LeadView;
import java.time.Instant;
import java.util.Map;

/** 명단 항목. visitorId, consentAt 같은 내부 정보는 내보내지 않는다. */
public record LeadResponse(Long id, Long formId, String formTitle, String channel, String name, String phone,
        String email, Map<String, Object> payload, Instant createdAt) {

    public static LeadResponse from(LeadView view) {
        var lead = view.lead();
        return new LeadResponse(lead.getId(), lead.getFormId(), view.formTitle(), lead.getChannel().value(),
            lead.getName(), lead.getPhone(), lead.getEmail(), lead.getPayload(), lead.getCreatedAt());
    }
}
