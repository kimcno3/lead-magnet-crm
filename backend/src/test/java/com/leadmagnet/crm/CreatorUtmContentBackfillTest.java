package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** V12(크리에이터 링크 utm_content) 백필 (ADR 0014). 공유 컨테이너와 따로 빈 DB를 V11까지 만들고 데이터를 넣은 뒤 V12를 적용한다. */
@Testcontainers
class CreatorUtmContentBackfillTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    void 기존_크리에이터_링크는_utm_content가_채워지고_자체_링크는_NULL로_남는다() {
        JdbcTemplate jdbc = freshDatabaseAtV11();
        long operator = jdbc.queryForObject(
            "INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operator + ") RETURNING id", Long.class);
        long campaign = jdbc.queryForObject(
            "INSERT INTO campaigns (name, created_by) VALUES ('c', " + operator + ") RETURNING id", Long.class);
        long form = jdbc.queryForObject("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text, status) "
            + "VALUES (?, ?, 'form-a', '폼', '동의', 'draft') RETURNING id", Long.class, campaign, template);
        long creator = jdbc.queryForObject(
            "INSERT INTO creators (name, created_by) VALUES ('김하늘', " + operator + ") RETURNING id", Long.class);
        jdbc.update("INSERT INTO distribution_links (form_id, channel, creator_id, code, utm_source) "
            + "VALUES (?, 'instagram', ?, 'code0001', 'instagram')", form, creator);
        jdbc.update("INSERT INTO distribution_links (form_id, channel, code, utm_source) "
            + "VALUES (?, 'instagram', 'code0002', 'instagram')", form);

        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '12'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT utm_content FROM distribution_links WHERE code = 'code0001'", String.class))
            .isEqualTo("creator-" + creator);
        assertThat(jdbc.queryForObject("SELECT utm_content FROM distribution_links WHERE code = 'code0002'", String.class))
            .isNull();
    }

    private static JdbcTemplate freshDatabaseAtV11() {
        DataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("11").load().migrate();
        return jdbc;
    }
}
