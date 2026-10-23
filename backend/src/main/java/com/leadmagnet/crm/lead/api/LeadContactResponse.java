package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadContactService.RevealedContact;
import java.util.Map;

/**
 * 신청자 원문 연락처. phone은 숫자만, email은 소문자다. 값이 없으면 null.
 * extraContacts는 phone·email 이외 이름의 tel·email 타입 또는 연락처 이름 입력의 원문(필드 이름 → 값, 없으면 {}, ADR 0015).
 */
public record LeadContactResponse(String name, String phone, String email, Map<String, String> extraContacts) {

    public static LeadContactResponse from(RevealedContact revealed) {
        return new LeadContactResponse(revealed.contact().name(), revealed.contact().phone(),
            revealed.contact().email(), revealed.extraContacts());
    }

    @Override
    public String toString() {
        return "LeadContactResponse[***]";
    }
}
