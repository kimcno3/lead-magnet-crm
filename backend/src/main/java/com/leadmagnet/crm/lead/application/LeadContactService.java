package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청자 원문 연락처 조회 (ADR 0015). 명단은 가린 값만 주고, 원문은 이 유스케이스로 한 건씩만 연다.
 * 원문 열람 기록(감사 로그)은 이 메서드를 감싸 붙인다.
 */
@Service
@Transactional(readOnly = true)
public class LeadContactService {

    private final LeadRepository leads;
    private final ContactProtector protector;

    public LeadContactService(LeadRepository leads, ContactProtector protector) {
        this.leads = leads;
        this.protector = protector;
    }

    /** 없는 신청은 404. */
    public LeadContact reveal(long leadId) {
        return leads.findById(leadId).map(protector::reveal)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
