package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.application.PublicFormService.OpenForm;
import com.leadmagnet.crm.template.domain.FormField;
import java.util.List;

/** 래퍼 스크립트가 쓰는 공개 폼. html은 iframe srcdoc에만 넣는 템플릿 원문이다. */
public record PublicFormResponse(String slug, String title, String consentText, List<FormField> fields, String html) {

    static PublicFormResponse from(OpenForm open) {
        return new PublicFormResponse(open.form().getSlug(), open.form().getTitle(), open.form().getConsentText(),
            open.form().getFieldConfig(), open.html());
    }
}
