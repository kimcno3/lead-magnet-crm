package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.template.domain.InspectionWarning;
import java.time.Instant;
import java.util.List;

public record TemplateResponse(Long id, String name, int size, String sha256, List<FormField> fields,
        List<InspectionWarning> inspection, int version, Instant createdAt, Instant updatedAt) {

    public static TemplateResponse from(HtmlTemplate t, List<InspectionWarning> inspection) {
        return new TemplateResponse(t.getId(), t.getName(), t.getSize(), t.getSha256(), t.getFields(), inspection,
            t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
