package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.InspectionWarning;
import java.util.List;

/** 저장 없이 돌려주는 검사 리포트: 추출될 필드와 경고. */
public record TemplateInspectionResponse(String name, int size, List<FormField> fields,
        List<InspectionWarning> inspection) {
}
