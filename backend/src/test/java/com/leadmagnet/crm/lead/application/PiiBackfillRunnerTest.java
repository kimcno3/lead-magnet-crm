package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** expand 이전에 들어온 평문만 있는 행을 암호문·해시로 채운다 (ADR 0015). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PiiBackfillRunnerTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PiiBackfillRunner runner;

    @Autowired
    PiiCipher cipher;

    @Autowired
    PiiHasher hasher;

    long formId;
    long campaignId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        long operatorId = id("INSERT INTO operators (email, password_hash) VALUES ('op@example.com', 'x') RETURNING id");
        long templateId = id("INSERT INTO html_templates (name, content, sha256, size, created_by) "
            + "VALUES ('t', '<form></form>', repeat('a', 64), 13, " + operatorId + ") RETURNING id");
        campaignId = id("INSERT INTO campaigns (name, created_by) VALUES ('c', " + operatorId + ") RETURNING id");
        formId = id("INSERT INTO forms (campaign_id, template_id, slug, title, consent_text) "
            + "VALUES (" + campaignId + ", " + templateId + ", 'legacy', '폼', '동의') RETURNING id");
    }

    @Test
    void 평문만_있는_행을_배치로_채우고_다시_돌려도_바꾸지_않는다(CapturedOutput output) {
        legacy("홍길동", "01012345678", "hong@example.com");
        legacy(null, "01022223333", null);
        legacy("김영희", null, "young@example.com");
        legacy("박철수", "01044445555", null);
        legacy("이민지", "01066667777", "minji@example.com");

        assertThat(runner.backfill(2)).isEqualTo(5);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM leads ORDER BY id");
        assertThat(rows).allSatisfy(row -> {
            assertThat(cipher.decrypt((byte[]) row.get("name_enc"), "name")).isEqualTo(row.get("name"));
            assertThat(cipher.decrypt((byte[]) row.get("phone_enc"), "phone")).isEqualTo(row.get("phone"));
            assertThat(cipher.decrypt((byte[]) row.get("email_enc"), "email")).isEqualTo(row.get("email"));
            assertThat(row.get("phone_hash")).isEqualTo(hasher.hashPhone((String) row.get("phone")));
            assertThat(row.get("email_hash")).isEqualTo(hasher.hashEmail((String) row.get("email")));
        });
        assertThat(rows.get(1)).containsEntry("name_enc", null).containsEntry("email_enc", null)
            .containsEntry("email_hash", null);

        byte[] before = jdbc.queryForObject("SELECT phone_enc FROM leads ORDER BY id LIMIT 1", byte[].class);
        assertThat(runner.backfill(2)).isZero();
        assertThat(jdbc.queryForObject("SELECT phone_enc FROM leads ORDER BY id LIMIT 1", byte[].class))
            .isEqualTo(before);

        assertThat(output.getAll()).contains("개인정보 백필 완료: 5건")
            .doesNotContain("홍길동").doesNotContain("01012345678").doesNotContain("hong@example.com")
            .doesNotContain("young@example.com");
    }

    @Test
    void 이미_암호문이_있는_새_행은_건드리지_않는다() {
        byte[] sealed = cipher.encrypt("01012345678", "phone");
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, phone, phone_enc, phone_hash, consent_at) "
            + "VALUES (?, ?, 'direct', '01012345678', ?, ?, now())", formId, campaignId, sealed,
            hasher.hashPhone("01012345678"));

        assertThat(runner.backfill(100)).isZero();
        assertThat(jdbc.queryForObject("SELECT phone_enc FROM leads", byte[].class)).isEqualTo(sealed);
    }

    @Test
    void 일부_컬럼만_비어_있어도_빈_컬럼을_채운다() {
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, name, phone, phone_enc, phone_hash, consent_at) "
            + "VALUES (?, ?, 'direct', '홍길동', '01012345678', ?, ?, now())", formId, campaignId,
            cipher.encrypt("01012345678", "phone"), hasher.hashPhone("01012345678"));

        assertThat(runner.backfill(100)).isEqualTo(1);
        assertThat(cipher.decrypt(jdbc.queryForObject("SELECT name_enc FROM leads", byte[].class), "name")).isEqualTo("홍길동");
    }

    @Test
    void 채울_행이_없으면_0건이다() {
        assertThat(runner.backfill(100)).isZero();
    }

    private void legacy(String name, String phone, String email) {
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, name, phone, email, consent_at) "
            + "VALUES (?, ?, 'direct', ?, ?, ?, now())", formId, campaignId, name, phone, email);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
