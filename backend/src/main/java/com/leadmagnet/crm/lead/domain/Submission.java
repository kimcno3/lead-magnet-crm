package com.leadmagnet.crm.lead.domain;

import java.util.Map;

/** 검증·정규화를 마친 신청 입력. name·phone·email은 컬럼 값이고, 나머지 입력은 payload다. */
public record Submission(String name, String phone, String email, Map<String, Object> payload) {
}
