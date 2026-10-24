package com.leadmagnet.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.demo.DemoDataSeeder.Outcome;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 기본 설정(app.demo.enabled 없음 = false)에서는 운영자가 있고 DB가 비어 있어도 아무것도 넣지 않는다 (ADR 0023). */
@IntegrationTest
class DemoDataDisabledTest {

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    DemoProperties properties;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void 기본_설정에서는_예시_데이터를_넣지_않는다() {
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));

        assertThat(properties.enabled()).isFalse();
        assertThat(seeder.seedIfEnabled().outcome()).isEqualTo(Outcome.DISABLED);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM html_templates", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaigns", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM creators", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Long.class)).isZero();
    }
}
