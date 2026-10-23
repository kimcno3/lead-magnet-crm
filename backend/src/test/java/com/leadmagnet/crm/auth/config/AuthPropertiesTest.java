package com.leadmagnet.crm.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** 기동 시 @ConfigurationProperties 바인딩이 실패하면 컨텍스트가 뜨지 않는다. 그 바인딩 규칙을 확인한다. */
class AuthPropertiesTest {

    @Test
    void JWT_SECRET이_32바이트_이상이면_바인딩된다() {
        AuthProperties properties = bind("a".repeat(32));

        assertThat(properties.jwtSecret()).hasSize(32);
        assertThat(properties.adminOrigin()).isEqualTo("http://localhost:5173");
        assertThat(properties.cookieSecure()).isFalse();
    }

    @Test
    void JWT_SECRET이_32바이트_미만이면_기동이_실패한다() {
        assertThatThrownBy(() -> bind("a".repeat(31)))
            .isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("32바이트");
    }

    @Test
    void 바이트_수는_UTF8_기준으로_센다() {
        // 11자지만 33바이트
        assertThat(bind("가".repeat(11)).jwtSecret()).hasSize(11);
        assertThatThrownBy(() -> bind("가".repeat(10))).isInstanceOf(BindException.class);
    }

    @Test
    void JWT_SECRET이_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(null)).isInstanceOf(BindException.class);
    }

    @Test
    void refresh_한도는_기본_분당_30이고_설정할_수_있으며_1_미만이면_기동이_실패한다() {
        assertThat(bind("a".repeat(32)).refreshRateLimit()).isEqualTo(30);
        assertThat(bind("a".repeat(32), Map.of("app.auth.refresh-rate-limit", "5")).refreshRateLimit()).isEqualTo(5);
        assertThatThrownBy(() -> bind("a".repeat(32), Map.of("app.auth.refresh-rate-limit", "0")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("refresh-rate-limit");
    }

    private static AuthProperties bind(String secret) {
        return bind(secret, Map.of());
    }

    private static AuthProperties bind(String secret, Map<String, String> extra) {
        Map<String, String> values = new HashMap<>(extra);
        values.put("app.auth.admin-origin", "http://localhost:5173");
        values.put("app.auth.cookie-secure", "false");
        if (secret != null) {
            values.put("app.auth.jwt-secret", secret);
        }
        return new Binder(new MapConfigurationPropertySource(values))
            .bindOrCreate("app.auth", AuthProperties.class);
    }
}
