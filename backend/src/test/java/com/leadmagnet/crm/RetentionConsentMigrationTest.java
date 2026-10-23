package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** V10(보존 기간·동의 증빙, ADR 0020). 공유 컨테이너와 따로 빈 DB를 V9까지 만들고 데이터를 넣은 뒤 V10을 적용한다. */
@Testcontainers
class RetentionConsentMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    static final String CONSENT = "개인정보 수집에 동의합니다";

    @Test
    void 기존_신청은_폼의_현재_동의_문구와_sha256으로_백필되고_캠페인은_365일이다() throws Exception {
        JdbcTemplate jdbc = freshDatabaseAtV9();
        Fixture f = fixture(jdbc);
        long lead = insertLead(jdbc, f);

        flyway(jdbc.getDataSource()).migrate();

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT consent_text, consent_sha256, anonymized_at FROM leads WHERE id = ?", lead);
        assertThat(row.get("consent_text")).isEqualTo(CONSENT);
        assertThat(row.get("consent_sha256")).isEqualTo(java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(CONSENT.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(row.get("anonymized_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT retention_days FROM campaigns WHERE id = ?", Integer.class, f.campaign))
            .isEqualTo(365);
    }

    @Test
    void 익명화되지_않은_행은_여전히_연락처가_필수이고_익명화된_행은_연락처_없이_둘_수_있다() {
        JdbcTemplate jdbc = freshDatabaseAtV9();
        Fixture f = fixture(jdbc);
        flyway(jdbc.getDataSource()).migrate();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, consent_at) "
            + "VALUES (?, ?, 'direct', now())", f.form, f.campaign)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, consent_at, anonymized_at) "
            + "VALUES (?, ?, 'direct', now(), now())", f.form, f.campaign);
    }

    @Test
    void 보존_기간은_1에서_3650일이고_동의_문구와_sha256은_함께_있거나_함께_없다() {
        JdbcTemplate jdbc = freshDatabaseAtV9();
        Fixture f = fixture(jdbc);
        flyway(jdbc.getDataSource()).migrate();

        assertThatThrownBy(() -> jdbc.update("UPDATE campaigns SET retention_days = 0"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE campaigns SET retention_days = 3651"))
            .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE campaigns SET retention_days = 3650");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, email_enc, consent_at, "
            + "consent_text) VALUES (?, ?, 'direct', '\\x01'::bytea, now(), '동의')", f.form, f.campaign))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    record Fixture(long campaign, long form) {
    }

    private static Fixture fixture(JdbcTemplate jdbc) {
        long operator = jdbc.queryForObject(
            "INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operator + ") RETURNING id", Long.class);
        long campaign = jdbc.queryForObject(
            "INSERT INTO campaigns (name, created_by) VALUES ('c', " + operator + ") RETURNING id", Long.class);
        long form = jdbc.queryForObject("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text, status, "
            + "published_html, published_template_sha256) VALUES (?, ?, 'form-a', '폼', ?, 'published', '<form></form>', "
            + "repeat('a', 64)) RETURNING id", Long.class, campaign, template, CONSENT);
        return new Fixture(campaign, form);
    }

    private static long insertLead(JdbcTemplate jdbc, Fixture f) {
        return jdbc.queryForObject("INSERT INTO leads (form_id, campaign_id, channel, phone_enc, phone_hash, consent_at) "
            + "VALUES (?, ?, 'direct', '\\x01'::bytea, repeat('b', 64), now()) RETURNING id", Long.class, f.form, f.campaign);
    }

    private static JdbcTemplate freshDatabaseAtV9() {
        DataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("9").load().migrate();
        return jdbc;
    }

    private static Flyway flyway(DataSource ds) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
    }
}
