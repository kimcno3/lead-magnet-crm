package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadContact;

/** 신청자 원문 연락처. phone은 숫자만, email은 소문자다. 값이 없으면 null. */
public record LeadContactResponse(String name, String phone, String email) {

    public static LeadContactResponse from(LeadContact contact) {
        return new LeadContactResponse(contact.name(), contact.phone(), contact.email());
    }

    @Override
    public String toString() {
        return "LeadContactResponse[***]";
    }
}
