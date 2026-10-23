package com.leadmagnet.crm.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** 출고 기본값(이메일·IP 쌍 5, 이메일 50, IP 20, 창 15분, 잠금 대기 2초)과 잘못된 값의 기동 실패 (ADR 0021). */
class LoginThrottlePropertiesTest {

    static final LoginThrottleProperties DEFAULTS =
        new LoginThrottleProperties(5, 50, 20, Duration.ofMinutes(15), Duration.ofSeconds(2));

    @Test
    void application_yml의_기본값은_쌍_5_이메일_50_IP_20_창_15분이다() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        LoginThrottleProperties properties = new Binder(ConfigurationPropertySources.from(sources))
            .bindOrCreate("app.auth.login-throttle", LoginThrottleProperties.class);

        assertThat(properties).isEqualTo(DEFAULTS);
    }

    @Test
    void 설정이_없어도_같은_기본값이다() {
        assertThat(bind(Map.of())).isEqualTo(DEFAULTS);
    }

    @Test
    void 설정한_값을_쓰고_잘못된_값이면_기동이_실패한다() {
        assertThat(bind(Map.of("app.auth.login-throttle.window", "30m")).window()).isEqualTo(Duration.ofMinutes(30));
        assertThatThrownBy(() -> bind(Map.of("app.auth.login-throttle.email-ip-max-failures", "0")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("email-ip-max-failures");
        assertThatThrownBy(() -> bind(Map.of("app.auth.login-throttle.window", "10s")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("window");
        assertThat(bind(Map.of("app.auth.login-throttle.lock-timeout", "500ms")).lockTimeout())
            .isEqualTo(Duration.ofMillis(500));
        assertThatThrownBy(() -> bind(Map.of("app.auth.login-throttle.lock-timeout", "0s")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("lock-timeout");
    }

    private static LoginThrottleProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
            .bindOrCreate("app.auth.login-throttle", LoginThrottleProperties.class);
    }
}
