package com.leadmagnet.crm.form.config;

import com.leadmagnet.crm.form.domain.FormToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 키 설정으로 {@link FormToken} 빈을 만든다 (ADR 0019). */
@Configuration(proxyBeanMethods = false)
public class FormTokenConfig {

    @Bean
    FormToken formToken(FormTokenProperties properties) {
        return new FormToken(properties.keyBytes());
    }
}
