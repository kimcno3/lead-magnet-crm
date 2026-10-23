package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditAction;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청자 원문 연락처 조회 (ADR 0015). 명단은 가린 값만 주고, 원문은 이 유스케이스로 한 건씩만 연다.
 * 열람은 같은 트랜잭션에서 감사 로그(lead.contact.view)에 남기고, 기록이 실패하면 원문도 주지 않는다(ADR 0016).
 */
@Service
public class LeadContactService {

    private final LeadRepository leads;
    private final ContactProtector protector;
    private final AuditService audit;

    public LeadContactService(LeadRepository leads, ContactProtector protector, AuditService audit) {
        this.leads = leads;
        this.protector = protector;
        this.audit = audit;
    }

    /** 없는 신청은 404(기록 없음). 복호화에 실패하면 예외가 그대로 올라가 기록도 롤백된다. */
    @Transactional
    public LeadContact reveal(long leadId, AuditActor actor) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        LeadContact contact = protector.reveal(lead);
        audit.recordRequired(actor, new AuditEvent(AuditAction.LEAD_CONTACT_VIEW, AuditEvent.TARGET_LEAD, lead.getId(),
            lead.getCampaignId(), Map.of()));
        return contact;
    }
}
