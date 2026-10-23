package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.application.PublicFormService.OpenForm;
import com.leadmagnet.crm.template.domain.FormField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 래퍼 스크립트가 쓰는 공개 폼. html은 iframe srcdoc에만 넣는 템플릿 원문이다.
 * ft는 작성 시간 토큰으로 래퍼가 제출에 그대로 실어 보낸다(ADR 0019).
 */
public record PublicFormResponse(String slug, String title, String consentText, List<FormField> fields, String html,
        @Schema(description = "작성 시간 토큰. 제출 본문의 ft로 보낸다. 발급 2초 뒤부터 24시간 동안 유효 (ADR 0019)")
        String ft) {

    static PublicFormResponse from(OpenForm open, String ft) {
        return new PublicFormResponse(open.form().getSlug(), open.form().getTitle(), open.form().getConsentText(),
            open.form().getFieldConfig(), open.html(), ft);
    }
}
