package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.template.application.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** public 출처의 폼 조회. published 폼만 열고, draft·없는 slug는 404, closed는 410이다. */
@Service
@Transactional(readOnly = true)
public class PublicFormService {

    private final FormRepository forms;
    private final TemplateService templates;

    public PublicFormService(FormRepository forms, TemplateService templates) {
        this.forms = forms;
        this.templates = templates;
    }

    /** 래퍼 페이지용: 공개 중인 폼. */
    public Form getOpen(String slug) {
        Form form = forms.findBySlug(slug).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        form.ensurePublic();
        return form;
    }

    /** 래퍼 스크립트용: 공개 중인 폼과 iframe srcdoc에 넣을 템플릿 원문. */
    public OpenForm getOpenWithHtml(String slug) {
        Form form = getOpen(slug);
        return new OpenForm(form, templates.get(form.getTemplateId()).getContent());
    }

    public record OpenForm(Form form, String html) {
    }
}
