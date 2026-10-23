package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.domain.FieldChange;
import java.util.List;

/** 없는(null) 필드는 바꾸지 않는다. fieldConfig 항목의 label·required도 없으면 현재 값을 유지한다. */
public record UpdateFormRequest(String title, String consentText, List<FieldChange> fieldConfig, String status) {
}
