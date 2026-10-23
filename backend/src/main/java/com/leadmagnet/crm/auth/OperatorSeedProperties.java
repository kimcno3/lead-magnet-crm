package com.leadmagnet.crm.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 기동 시 만들 운영자 계정. 환경 변수 ADMIN_SEED_EMAIL, ADMIN_SEED_PASSWORD로 받는다 (ADR 0009). */
@ConfigurationProperties(prefix = "app.seed.operator")
public record OperatorSeedProperties(String email, String password) {

    public boolean isEmpty() {
        return isBlank(email) && isBlank(password);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
