package com.leadmagnet.crm.lead.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 신청 요청. fields의 값은 문자열이거나(checkbox는) 문자열 배열이다.
 * consent는 JSON true만 동의로 본다("true" 문자열이나 1은 동의가 아니다).
 * fields.hp는 honeypot이고 ft는 폼 조회 때 받은 작성 시간 토큰이다(ADR 0019).
 */
public record SubmissionRequest(
        @Schema(description = "필드 이름 → 문자열 또는 문자열 배열(checkbox). hp는 honeypot(래퍼가 항상 빈 문자열로 보낸다)")
        Map<String, Object> fields,
        @Schema(description = "개인정보 수집·이용 동의. true여야 한다", type = "boolean") Object consent,
        @Schema(description = "배포 링크 code(/f/{slug}?lm=...). 없으면 direct") String lm,
        @Schema(description = "작성 시간 토큰. GET /api/public/forms/{slug} 응답의 ft 그대로") String ft) {

    boolean consented() {
        return Boolean.TRUE.equals(consent);
    }
}
