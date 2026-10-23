package com.leadmagnet.crm.form.api;

import com.leadmagnet.crm.form.application.TemplateVersionService.NewVersion;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.template.domain.InspectionWarning;
import java.time.Instant;
import java.util.List;

/** 새 버전 업로드 결과. affectedDraftForms는 새 필드로 다시 스냅샷된 draft 폼 수다. */
public record TemplateVersionResponse(Long id, String name, int size, String sha256, List<FormField> fields,
        List<InspectionWarning> inspection, int version, Instant updatedAt, int affectedDraftForms) {

    static TemplateVersionResponse from(NewVersion v) {
        HtmlTemplate t = v.template();
        return new TemplateVersionResponse(t.getId(), t.getName(), t.getSize(), t.getSha256(), t.getFields(),
            v.inspection(), t.getVersion(), t.getUpdatedAt(), v.affectedDraftForms());
    }
}
