package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.template.domain.FormField;
import java.util.List;

/** 없는(null) 필드는 바꾸지 않는다. */
public record UpdateFormRequest(String title, String consentText, List<FormField> fieldConfig, String status) {
}
