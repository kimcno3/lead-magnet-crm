package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import java.time.Instant;
import java.util.List;

public record TemplateResponse(Long id, String name, int size, String sha256, List<FormField> fields,
        Instant createdAt) {

    public static TemplateResponse from(HtmlTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getSize(), t.getSha256(), t.getFields(), t.getCreatedAt());
    }
}
