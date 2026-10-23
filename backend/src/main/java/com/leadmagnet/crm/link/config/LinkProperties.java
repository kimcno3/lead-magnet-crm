package com.leadmagnet.crm.link.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 배포 링크 URL의 앞부분. 환경 변수 PUBLIC_BASE_URL. 끝의 /는 무시한다. */
@Validated
@ConfigurationProperties(prefix = "app")
public record LinkProperties(@NotBlank String publicBaseUrl) {

    public String baseUrl() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }
}
