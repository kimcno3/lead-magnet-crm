package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.PiiMasker;

/** 신청자 원문 연락처. 전화번호는 숫자만, 이메일은 소문자로 정규화된 값이다. 로그에 남기지 않는다. */
public record LeadContact(String name, String phone, String email) {

    /** 명단 화면용으로 가린 값 (ADR 0015). */
    public LeadContact masked() {
        return new LeadContact(PiiMasker.maskName(name), PiiMasker.maskPhoneForDisplay(phone),
            PiiMasker.maskEmailForDisplay(email));
    }

    @Override
    public String toString() {
        return "LeadContact[***]";
    }
}
