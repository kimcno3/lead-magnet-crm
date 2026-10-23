package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청자가 동의한 문구 증빙 조회 (ADR 0020). 문구는 신청자 개인정보가 아니므로 원문 열람(lead.contact.view)과 따로 두고
 * 감사 기록을 남기지 않는다. 파기된 신청도 증빙은 남아 있어 조회할 수 있다.
 */
@Service
public class LeadConsentService {

    private final LeadRepository leads;

    public LeadConsentService(LeadRepository leads) {
        this.leads = leads;
    }

    /** 없는 신청은 404. */
    @Transactional(readOnly = true)
    public LeadConsent get(long leadId) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new LeadConsent(lead.getConsentText(), lead.getConsentSha256(), lead.getConsentAt(),
            lead.getAnonymizedAt());
    }

    /** consentText·consentSha256은 V10 백필로도 채우지 못한 신청이면 null이다. */
    public record LeadConsent(String consentText, String consentSha256, Instant consentAt, Instant anonymizedAt) {
    }
}
