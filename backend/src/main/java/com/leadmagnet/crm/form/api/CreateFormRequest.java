package com.leadmagnet.crm.form.api;

public record CreateFormRequest(Long templateId, String title, String consentText, String slug) {
}
