package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadConsentService.LeadConsent;
import java.time.Instant;

/**
 * 신청자가 동의한 문구 원문, 그 sha256(UTF-8, 소문자 hex), 동의 시각(= 신청 시각). anonymizedAt은 파기된 신청이면 그 시각이다.
 * 문구·sha256은 V10 이전 신청에서 백필하지 못했으면 null이다(ADR 0020).
 */
public record LeadConsentResponse(String consentText, String consentSha256, Instant consentAt, Instant anonymizedAt) {

    public static LeadConsentResponse from(LeadConsent consent) {
        return new LeadConsentResponse(consent.consentText(), consent.consentSha256(), consent.consentAt(),
            consent.anonymizedAt());
    }
}
