package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청자 원문 연락처 조회 (ADR 0015). 명단은 가린 값만 주고, 원문은 이 유스케이스로 한 건씩만 연다.
 * payload의 연락처처럼 보이는 추가 항목(extraContacts)도 여기서만 원문으로 준다. 열람은 같은 트랜잭션에서 감사 로그(lead.contact.view)에 남기고, 기록이 실패하면 원문도 주지 않는다(ADR 0016).
 */
@Service
public class LeadContactService {

    private final LeadRepository leads;
    private final ContactProtector protector;
    private final AuditService audit;
    private final FormRepository forms;

    public LeadContactService(LeadRepository leads, ContactProtector protector, AuditService audit,
            FormRepository forms) {
        this.leads = leads;
        this.forms = forms;
        this.protector = protector;
        this.audit = audit;
    }

    /**
     * 없는 신청은 404, 보존 기간이 지나 파기된 신청은 410 lead_anonymized(둘 다 기록 없음, 보여 준 것이 없다).
     * 복호화에 실패하면 예외가 그대로 올라가 기록도 롤백된다.
     */
    @Transactional
    public RevealedContact reveal(long leadId, AuditActor actor) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (lead.isAnonymized()) {
            throw new DomainException(ErrorCode.LEAD_ANONYMIZED);
        }
        LeadContact contact = protector.reveal(lead);
        Map<String, String> extra = ExtraContacts.raw(lead.getPayload(),
            forms.findById(lead.getFormId()).map(Form::getFieldConfig).orElse(null));
        audit.recordRequired(actor, AuditEvent.contactView(lead.getId(), lead.getCampaignId()));
        return new RevealedContact(contact, extra);
    }

    /** 원문 연락처와, payload에 남은 연락처처럼 보이는 추가 항목의 원문(필드 이름 → 값, 없으면 빈 맵). */
    public record RevealedContact(LeadContact contact, Map<String, String> extraContacts) {

        @Override
        public String toString() {
            return "RevealedContact[***]";
        }
    }
}
