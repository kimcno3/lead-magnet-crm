package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** public 출처의 폼 조회. published 폼만 열고, draft·없는 slug는 404, closed는 410이다. */
@Service
@Transactional(readOnly = true)
public class PublicFormService {

    private final FormRepository forms;

    public PublicFormService(FormRepository forms) {
        this.forms = forms;
    }

    /** 래퍼 페이지용: 공개 중인 폼. */
    public Form getOpen(String slug) {
        Form form = forms.findBySlug(slug).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        form.ensurePublic();
        return form;
    }

    /** 래퍼 스크립트용: 공개 중인 폼과 iframe srcdoc에 넣을 원문. 템플릿이 아니라 게시 시점 스냅샷을 쓴다(ADR 0018). */
    public OpenForm getOpenWithHtml(String slug) {
        Form form = getOpen(slug);
        return new OpenForm(form, form.getPublishedHtml());
    }

    public record OpenForm(Form form, String html) {
    }
}
