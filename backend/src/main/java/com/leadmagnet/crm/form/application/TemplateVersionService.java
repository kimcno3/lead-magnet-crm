package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.form.domain.FormStatus;
import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.template.domain.InspectionWarning;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 템플릿 새 버전 업로드(ADR 0018). 템플릿을 바꾸고 이 템플릿을 쓰는 draft 폼의 필드를 같은 트랜잭션에서 다시 스냅샷한다.
 * 게시된 폼과 마감된 폼은 자기 스냅샷을 쓰므로 건드리지 않는다. 폼과 템플릿 양쪽을 다뤄서 form 패키지에 둔다.
 */
@Service
@Transactional
public class TemplateVersionService {

    private final TemplateService templates;
    private final FormRepository forms;
    private final Clock clock;

    public TemplateVersionService(TemplateService templates, FormRepository forms, Clock clock) {
        this.templates = templates;
        this.forms = forms;
        this.clock = clock;
    }

    public record NewVersion(HtmlTemplate template, List<InspectionWarning> inspection, int affectedDraftForms) {
    }

    /**
     * 업로드와 같은 파일 검사(계약·크기·UTF-8·경고)를 먼저 하고, 실패하면 아무것도 바꾸지 않는다.
     * 잠금 순서는 템플릿 → 폼이다. 게시({@code FormService.update})도 같은 순서라, 게시는 이 변경이 커밋되기 전/후의
     * 템플릿과 필드를 한 쌍으로 읽는다.
     */
    public NewVersion upload(long templateId, String filename, byte[] bytes) {
        TemplateService.Inspected checked = templates.inspect(filename, bytes, null);
        HtmlTemplate template = templates.getForUpdate(templateId);
        List<FormField> previousFields = template.getFields();
        var now = clock.instant();
        template.newVersion(checked.content(), checked.fields(), checked.inspection(), now);
        List<Form> drafts = forms.findAllByTemplateIdAndStatusOrderByIdAsc(templateId, FormStatus.DRAFT);
        drafts.forEach(form -> form.resnapshotFields(previousFields, checked.fields(), now));
        return new NewVersion(template, checked.inspection(), drafts.size());
    }
}
