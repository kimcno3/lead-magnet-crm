package com.leadmagnet.crm.template.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.template.max-bytes}: 업로드 HTML 최대 바이트(기본 512KB). */
@ConfigurationProperties("app.template")
public record TemplateProperties(int maxBytes) {

    public TemplateProperties {
        if (maxBytes <= 0) {
            maxBytes = 524288;
        }
    }
}
