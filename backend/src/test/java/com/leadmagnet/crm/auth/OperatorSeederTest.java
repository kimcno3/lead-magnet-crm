package com.leadmagnet.crm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.auth.OperatorSeeder.InvalidSeedConfigurationException;
import com.leadmagnet.crm.auth.OperatorSeeder.SeedResult;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OperatorSeederTest {

    static final String EMAIL = "Owner@Example.com";
    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void 설정이_있으면_소문자_이메일과_bcrypt_해시로_운영자를_만든다() {
        SeedResult result = seeder(EMAIL, PASSWORD).seed();

        assertThat(result).isEqualTo(SeedResult.CREATED);
        Operator saved = operators.findByEmail("owner@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void 다시_실행해도_중복으로_만들지_않고_기존_비밀번호를_덮어쓰지_않는다() {
        seeder(EMAIL, PASSWORD).seed();
        String originalHash = operators.findByEmail("owner@example.com").orElseThrow().getPasswordHash();

        SeedResult result = seeder("owner@example.com", "another-long-password").seed();

        assertThat(result).isEqualTo(SeedResult.ALREADY_EXISTS);
        assertThat(operators.count()).isEqualTo(1);
        assertThat(operators.findByEmail("owner@example.com").orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void 설정이_없으면_아무것도_만들지_않는다() {
        assertThat(seeder("", "").seed()).isEqualTo(SeedResult.SKIPPED);
        assertThat(seeder(null, null).seed()).isEqualTo(SeedResult.SKIPPED);
        assertThat(operators.count()).isZero();
    }

    @Test
    void 이메일이나_비밀번호_한쪽만_있으면_기동을_멈춘다() {
        assertThatThrownBy(() -> seeder(EMAIL, "").seed()).isInstanceOf(InvalidSeedConfigurationException.class);
        assertThatThrownBy(() -> seeder("", PASSWORD).seed()).isInstanceOf(InvalidSeedConfigurationException.class);
        assertThat(operators.count()).isZero();
    }

    @Test
    void 짧거나_72바이트를_넘는_비밀번호는_거부한다() {
        assertThatThrownBy(() -> seeder(EMAIL, "short-pw").seed())
            .isInstanceOf(InvalidSeedConfigurationException.class);
        assertThatThrownBy(() -> seeder(EMAIL, "가".repeat(25)).seed())
            .isInstanceOf(InvalidSeedConfigurationException.class);
        assertThat(operators.count()).isZero();
    }

    @Test
    void 이메일_형식이_잘못되면_거부한다() {
        assertThatThrownBy(() -> seeder("not-an-email", PASSWORD).seed())
            .isInstanceOf(InvalidSeedConfigurationException.class);
        assertThatThrownBy(() -> seeder("a@b@c", PASSWORD).seed())
            .isInstanceOf(InvalidSeedConfigurationException.class);
    }

    @Test
    void 로그에는_이메일_원문과_비밀번호를_남기지_않는다(CapturedOutput output) {
        seeder(EMAIL, PASSWORD).seed();
        seeder(EMAIL, PASSWORD).seed();

        assertThat(output.getAll())
            .contains("o***@example.com")
            .doesNotContainIgnoringCase("owner@example.com")
            .doesNotContain(PASSWORD);
    }

    private OperatorSeeder seeder(String email, String password) {
        return new OperatorSeeder(new OperatorSeedProperties(email, password), operators, passwordEncoder);
    }
}
