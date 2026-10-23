package com.leadmagnet.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SchemaMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    long operatorId;
    long formId;
    long linkId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = insertId("INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id");
        long templateId = insertId("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operatorId + ") RETURNING id");
        long campaignId = insertId("INSERT INTO campaigns (name, created_by) VALUES ('c', " + operatorId + ") RETURNING id");
        formId = insertId("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text) "
            + "VALUES (" + campaignId + ", " + templateId + ", 'spring-event', '폼', '동의') RETURNING id");
        linkId = insertId("INSERT INTO distribution_links (form_id, channel, code) "
            + "VALUES (" + formId + ", 'instagram', 'abc123') RETURNING id");
    }

    @Test
    void V1_마이그레이션이_적용되어_모든_테이블이_있다() {
        Boolean applied = jdbc.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
        assertThat(applied).isTrue();

        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("operators", "refresh_tokens", "html_templates", "campaigns", "forms",
            "distribution_links", "visits", "leads");
    }

    @Test
    void 정상_방문과_신청을_저장할_수_있다() {
        jdbc.update("INSERT INTO visits (form_id, link_id, channel, visitor_id) VALUES (?, ?, 'instagram', ?)",
            formId, linkId, UUID.randomUUID());
        jdbc.update("INSERT INTO visits (form_id, channel, visitor_id) VALUES (?, 'direct', ?)",
            formId, UUID.randomUUID());
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, phone, payload, consent_at) "
            + "SELECT id, campaign_id, 'direct', '01012345678', '{\"memo\":\"hi\"}'::jsonb, now() FROM forms WHERE id = ?",
            formId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT payload->>'memo' FROM leads", String.class)).isEqualTo("hi");
    }

    @Test
    void V2_마이그레이션이_같은_폼의_같은_전화번호와_이메일을_막고_NULL은_허용한다() {
        Boolean applied = jdbc.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '2'", Boolean.class);
        assertThat(applied).isTrue();
        String insert = "INSERT INTO leads (form_id, campaign_id, channel, phone, email, consent_at) "
            + "SELECT id, campaign_id, 'direct', %s, %s, now() FROM forms WHERE id = " + formId;
        jdbc.update(insert.formatted("'01011112222'", "'a@example.com'"));

        assertRejected(insert.formatted("'01011112222'", "'b@example.com'"));
        assertRejected(insert.formatted("'01033334444'", "'a@example.com'"));
        jdbc.update(insert.formatted("'01033334444'", "NULL"));
        jdbc.update(insert.formatted("NULL", "'c@example.com'"));
        jdbc.update(insert.formatted("'01055556666'", "NULL"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads", Long.class)).isEqualTo(4);
    }

    @Test
    void 허용되지_않은_채널의_배포_링크는_거부한다() {
        assertRejected("INSERT INTO distribution_links (form_id, channel, code) VALUES (" + formId + ", 'tiktok', 'zzz999')");
    }

    @Test
    void 배포_링크_code는_중복될_수_없다() {
        assertRejected("INSERT INTO distribution_links (form_id, channel, code) VALUES (" + formId + ", 'x', 'abc123')");
    }

    @Test
    void 폼_상태와_slug_형식을_검사한다() {
        long campaignId = jdbc.queryForObject("SELECT campaign_id FROM forms WHERE id = ?", Long.class, formId);
        long templateId = jdbc.queryForObject("SELECT template_id FROM forms WHERE id = ?", Long.class, formId);
        assertRejected("UPDATE forms SET status = 'deleted' WHERE id = " + formId);
        assertRejected("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text) VALUES ("
            + campaignId + ", " + templateId + ", 'Bad Slug!', 't', 'c')");
        assertRejected("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text) VALUES ("
            + campaignId + ", " + templateId + ", 'spring-event', 't', 'c')");
    }

    @Test
    void 링크와_채널이_어긋난_방문은_거부한다() {
        assertRejected("INSERT INTO visits (form_id, link_id, channel, visitor_id) VALUES ("
            + formId + ", " + linkId + ", 'direct', gen_random_uuid())");
        assertRejected("INSERT INTO visits (form_id, channel, visitor_id) VALUES ("
            + formId + ", 'instagram', gen_random_uuid())");
    }

    @Test
    void 연락처가_없는_신청은_거부한다() {
        assertRejected("INSERT INTO leads (form_id, campaign_id, channel, name, consent_at) "
            + "SELECT id, campaign_id, 'direct', '홍길동', now() FROM forms WHERE id = " + formId);
    }

    @Test
    void 운영자_이메일은_소문자로만_저장하고_중복될_수_없다() {
        assertRejected("INSERT INTO operators (email, password_hash) VALUES ('Op@Example.com', 'x')");
        assertRejected("INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x')");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
