package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** 통합 테스트는 public 포트를 임의 포트(0)로 바꾸므로 기본값은 application.yml만 읽어 확인한다. */
class PortsPropertiesTest {

    @Test
    void 출처별_포트_기본값은_8080과_8081이다() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);

        PortsProperties ports = Binder.get(environment).bind("app.ports", PortsProperties.class).get();

        assertThat(ports.admin()).isEqualTo(8080);
        assertThat(ports.publicPort()).isEqualTo(8081);
    }
}
