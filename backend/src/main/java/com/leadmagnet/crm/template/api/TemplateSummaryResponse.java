package com.leadmagnet.crm.template.api;

import com.leadmagnet.crm.template.domain.HtmlTemplate;
import java.time.Instant;

public record TemplateSummaryResponse(Long id, String name, int size, int fieldCount, Instant createdAt) {

    public static TemplateSummaryResponse from(HtmlTemplate t) {
        return new TemplateSummaryResponse(t.getId(), t.getName(), t.getSize(), t.getFields().size(), t.getCreatedAt());
    }
}
