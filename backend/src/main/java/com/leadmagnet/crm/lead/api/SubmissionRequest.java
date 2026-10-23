package com.leadmagnet.crm.lead.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 신청 요청. fields의 값은 문자열이거나(checkbox는) 문자열 배열이다.
 * consent는 JSON true만 동의로 본다("true" 문자열이나 1은 동의가 아니다).
 */
public record SubmissionRequest(
        @Schema(description = "필드 이름 → 문자열 또는 문자열 배열(checkbox)") Map<String, Object> fields,
        @Schema(description = "개인정보 수집·이용 동의. true여야 한다", type = "boolean") Object consent,
        @Schema(description = "배포 링크 code(/f/{slug}?lm=...). 없으면 direct") String lm) {

    boolean consented() {
        return Boolean.TRUE.equals(consent);
    }
}
