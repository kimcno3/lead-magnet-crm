package com.leadmagnet.crm.lead.api;

/** 접수 결과. 신청 id 같은 내부 정보는 돌려주지 않는다. */
public record SubmissionResponse(String status) {

    public static SubmissionResponse received() {
        return new SubmissionResponse("received");
    }
}
