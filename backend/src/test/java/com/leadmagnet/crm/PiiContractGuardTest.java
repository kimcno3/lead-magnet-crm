package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V5(평문 컬럼 삭제) 가드 (ADR 0015). 공유 컨테이너와 따로 빈 DB를 띄워 V4까지 적용한 뒤 데이터를 넣고 V5를 시도한다.
 * 컨테이너 하나를 테스트마다 비워 쓰므로 각 테스트가 스키마를 새로 만든다.
 */
@Testcontainers
class PiiContractGuardTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    void 백필되지_않은_평문_행이_있으면_V5가_가드_메시지로_중단되고_평문은_그대로다() {
        JdbcTemplate jdbc = freshDatabaseAtV4();
        long formId = seedForm(jdbc);
        // 14a 이전에 쌓인 행: 평문만 있고 암호문·해시가 없다.
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, name, phone, consent_at) "
            + "SELECT id, campaign_id, 'direct', '홍길동', '01012345678', now() FROM forms WHERE id = ?", formId);

        assertThatThrownBy(() -> flyway(dataSource(jdbc), null).migrate())
            .hasMessageContaining("V5 중단").hasMessageContaining("백필");

        assertThat(jdbc.queryForObject("SELECT phone FROM leads", String.class)).isEqualTo("01012345678");
        assertThat(jdbc.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success", Integer.class))
            .isEqualTo(4);
    }

    @Test
    void 이름만_백필되지_않았거나_해시만_빠져도_V5가_중단된다() {
        JdbcTemplate jdbc = freshDatabaseAtV4();
        long formId = seedForm(jdbc);
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, name, phone, phone_enc, phone_hash, consent_at) "
            + "SELECT id, campaign_id, 'direct', '홍길동', '01012345678', '\\x01'::bytea, repeat('a', 64), now() "
            + "FROM forms WHERE id = ?", formId);

        assertThatThrownBy(() -> flyway(dataSource(jdbc), null).migrate()).hasMessageContaining("V5 중단");

        jdbc.update("UPDATE leads SET name = NULL, phone_hash = NULL");
        assertThatThrownBy(() -> flyway(dataSource(jdbc), null).migrate()).hasMessageContaining("V5 중단");
    }

    @Test
    void 백필된_행만_있으면_V5가_적용되고_평문_컬럼이_사라진다() {
        JdbcTemplate jdbc = freshDatabaseAtV4();
        long formId = seedForm(jdbc);
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, name, phone, email, name_enc, phone_enc, email_enc, "
            + "phone_hash, email_hash, consent_at) SELECT id, campaign_id, 'direct', '홍길동', '01012345678', 'a@example.com', "
            + "'\\x01'::bytea, '\\x02'::bytea, '\\x03'::bytea, repeat('a', 64), repeat('b', 64), now() FROM forms WHERE id = ?",
            formId);
        // 이메일만 있고 모두 백필된 행도 가드를 통과한다.
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, email, email_enc, email_hash, consent_at) "
            + "SELECT id, campaign_id, 'direct', 'b@example.com', '\\x04'::bytea, repeat('c', 64), now() FROM forms WHERE id = ?",
            formId);

        flyway(dataSource(jdbc), null).migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'leads'",
            String.class)).doesNotContain("name", "phone", "email").contains("name_enc", "phone_enc", "email_enc");
    }

    @Test
    void 빈_DB는_V5까지_적용된다() {
        JdbcTemplate jdbc = freshDatabaseAtV4();

        flyway(dataSource(jdbc), null).migrate();

        // 뒤에 마이그레이션이 더해져도(V6 감사 로그 등) V5 가드가 빈 DB를 통과시키는지만 본다.
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '5'", Boolean.class))
            .isTrue();
    }

    private static JdbcTemplate freshDatabaseAtV4() {
        DataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        flyway(ds, "4").migrate();
        return jdbc;
    }

    private static DataSource dataSource(JdbcTemplate jdbc) {
        return jdbc.getDataSource();
    }

    private static Flyway flyway(DataSource ds, String target) {
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static long seedForm(JdbcTemplate jdbc) {
        long operator = jdbc.queryForObject(
            "INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id", Long.class);
        long template = jdbc.queryForObject("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operator + ") RETURNING id", Long.class);
        long campaign = jdbc.queryForObject(
            "INSERT INTO campaigns (name, created_by) VALUES ('c', " + operator + ") RETURNING id", Long.class);
        return jdbc.queryForObject("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text) VALUES ("
            + campaign + ", " + template + ", 'spring-event', '폼', '동의') RETURNING id", Long.class);
    }
}
