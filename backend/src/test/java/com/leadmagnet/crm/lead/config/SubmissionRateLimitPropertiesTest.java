package com.leadmagnet.crm.lead.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** 출고 기본값(IP 10, 방문자 5, 키 10,000)과 잘못된 값의 기동 실패. */
class SubmissionRateLimitPropertiesTest {

    @Test
    void application_yml의_기본값은_IP_10_방문자_5_키_10000이다() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        SubmissionRateLimitProperties properties = new Binder(ConfigurationPropertySources.from(sources))
            .bindOrCreate("app.public.rate-limit", SubmissionRateLimitProperties.class);

        assertThat(properties).isEqualTo(new SubmissionRateLimitProperties(10, 5, 10_000));
    }

    @Test
    void 설정이_없어도_같은_기본값이다() {
        assertThat(bind(Map.of())).isEqualTo(new SubmissionRateLimitProperties(10, 5, 10_000));
    }

    @Test
    void 설정한_값을_쓰고_1보다_작으면_기동이_실패한다() {
        assertThat(bind(Map.of("app.public.rate-limit.ip-per-minute", "30")).ipPerMinute()).isEqualTo(30);
        assertThatThrownBy(() -> bind(Map.of("app.public.rate-limit.visitor-per-minute", "0")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("visitor-per-minute");
    }

    private static SubmissionRateLimitProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
            .bindOrCreate("app.public.rate-limit", SubmissionRateLimitProperties.class);
    }
}
