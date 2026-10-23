package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.domain.AuditAction;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 감사 로그 조회의 action 파라미터 허용 값을 {@link AuditAction}에서 채워, 손으로 적은 목록이 enum과 어긋나지 않게 한다. */
@Configuration(proxyBeanMethods = false)
public class AuditOpenApiConfig {

    static final String PATH = "/api/admin/audit-logs";

    @Bean
    OpenApiCustomizer auditActionValues() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getPaths().get(PATH) == null
                || openApi.getPaths().get(PATH).getGet() == null
                || openApi.getPaths().get(PATH).getGet().getParameters() == null) {
                return;
            }
            openApi.getPaths().get(PATH).getGet().getParameters().stream()
                .filter(p -> "action".equals(p.getName()))
                .forEach(p -> {
                    StringSchema schema = new StringSchema();
                    schema.setEnum(Arrays.stream(AuditAction.values()).map(AuditAction::value).toList());
                    p.setSchema(schema);
                });
        };
    }
}
