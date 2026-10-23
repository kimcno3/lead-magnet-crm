package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** V8(게시 스냅샷) 백필 (ADR 0018). 공유 컨테이너와 따로 빈 DB를 V7까지 만들고 데이터를 넣은 뒤 V8을 적용한다. */
@Testcontainers
class SnapshotBackfillTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    void 이미_게시되거나_마감된_폼은_현재_템플릿으로_스냅샷이_채워지고_draft는_비어_있다() {
        JdbcTemplate jdbc = freshDatabaseAtV7();
        long operator = jdbc.queryForObject(
            "INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form>현재</form>', repeat('b', 64), 20, " + operator + ") RETURNING id", Long.class);
        long campaign = jdbc.queryForObject(
            "INSERT INTO campaigns (name, created_by) VALUES ('c', " + operator + ") RETURNING id", Long.class);
        for (String status : new String[] {"draft", "published", "closed"}) {
            jdbc.update("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text, status) "
                + "VALUES (?, ?, ?, '폼', '동의', ?)", campaign, template, "form-" + status, status);
        }

        flyway(jdbc.getDataSource()).migrate();

        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '8'", Boolean.class)).isTrue();
        for (String status : new String[] {"published", "closed"}) {
            assertThat(jdbc.queryForObject("SELECT published_html FROM forms WHERE slug = ?", String.class, "form-" + status))
                .isEqualTo("<form>현재</form>");
            assertThat(jdbc.queryForObject("SELECT published_template_sha256 FROM forms WHERE slug = ?", String.class,
                "form-" + status)).isEqualTo("b".repeat(64));
        }
        assertThat(jdbc.queryForObject("SELECT published_html FROM forms WHERE slug = 'form-draft'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT published_template_sha256 FROM forms WHERE slug = 'form-draft'", String.class))
            .isNull();
        // 기존 템플릿은 버전 1, updated_at은 created_at과 같다.
        assertThat(jdbc.queryForObject("SELECT version FROM html_templates", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_at = created_at FROM html_templates", Boolean.class)).isTrue();
    }

    @Test
    void 스냅샷_없이_게시_상태로_저장하려_하면_제약이_막는다() {
        JdbcTemplate jdbc = freshDatabaseAtV7();
        long operator = jdbc.queryForObject(
            "INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operator + ") RETURNING id", Long.class);
        long campaign = jdbc.queryForObject(
            "INSERT INTO campaigns (name, created_by) VALUES ('c', " + operator + ") RETURNING id", Long.class);
        flyway(jdbc.getDataSource()).migrate();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text, status) "
            + "VALUES (?, ?, 'no-snapshot', '폼', '동의', 'published')", campaign, template))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static JdbcTemplate freshDatabaseAtV7() {
        DataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("7").load().migrate();
        return jdbc;
    }

    private static Flyway flyway(DataSource ds) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
    }
}
