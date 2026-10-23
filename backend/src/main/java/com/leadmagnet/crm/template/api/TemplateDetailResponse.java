package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import java.time.Instant;
import java.util.List;

public record TemplateDetailResponse(Long id, String name, int size, String sha256, List<FormField> fields,
        String content, Instant createdAt) {

    public static TemplateDetailResponse from(HtmlTemplate t) {
        return new TemplateDetailResponse(t.getId(), t.getName(), t.getSize(), t.getSha256(), t.getFields(),
            t.getContent(), t.getCreatedAt());
    }
}
