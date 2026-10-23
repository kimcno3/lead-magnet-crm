package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 재암호화 중간에 배치가 실패해도 이미 커밋한 배치는 남고, 그 건수로 감사 기록(completed=false, 예외 종류)을 먼저 남긴 뒤
 * 예외를 올린다 (ADR 0022). 배치는 2건이고 두 번째 배치의 대상 조회를 실패시킨다. 표식은 이전 키로 남는다.
 */
@IntegrationTest
class PiiReencryptionFailureTest {

    @LocalServerPort
    int adminPort;

    @Autowired
    PublicPort publicPort;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PiiKeyCheck keyCheck;

    @Autowired
    PiiReencryptionService reencryption;

    @MockitoSpyBean
    LeadKeyRotationRows rows;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        String token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        AdminFixtures.createPublishedForm(token, "form-a");
        PiiCipher previous = new PiiCipher(1, Map.of(1, Base64.getDecoder().decode(IntegrationTest.TEST_PREVIOUS_ENC_KEY)));
        for (int i = 0; i < 3; i++) {
            PublicOrigin.given(publicPort).contentType(ContentType.JSON)
                .body(Map.of("fields", Map.of("name", "홍길동", "phone", "010-7777-000" + i), "consent", true,
                    "ft", FormTokens.mature(publicPort, clock, "form-a")))
                .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        }
        jdbc.update("UPDATE leads SET name_enc = ?, phone_enc = ?", previous.encrypt("홍길동", "name"),
            previous.encrypt("01077770000", "phone"));
        jdbc.update("UPDATE pii_key_check SET enc_check = ? WHERE id = 1",
            previous.encrypt("lead-magnet-crm:pii-key-check", "pii_key_check"));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                return invocation.callRealMethod();
            }
            throw new DataAccessResourceFailureException("db down");
        }).when(rows).findNotOnKey(anyInt(), anyLong(), anyInt());
    }

    @AfterEach
    void restore() {
        clock.reset();
        jdbc.update("DELETE FROM pii_key_check");
        keyCheck.verify();
    }

    @Test
    void 두_번째_배치가_실패하면_첫_배치는_바뀐_채_부분_건수와_completed_false를_기록하고_예외를_올린다() {
        assertThatThrownBy(() -> reencryption.run(new AuditActor(1L, null, null)))
            .isInstanceOf(DataAccessResourceFailureException.class);

        List<byte[]> phones = jdbc.queryForList("SELECT phone_enc FROM leads ORDER BY id", byte[].class);
        assertThat(PiiCipher.keyIdOf(phones.get(0))).isEqualTo(2);
        assertThat(PiiCipher.keyIdOf(phones.get(1))).isEqualTo(2);
        assertThat(PiiCipher.keyIdOf(phones.get(2))).isEqualTo(1);
        assertThat(keyCheck.markerOnCurrentKey()).isFalse();
        assertThat(jdbc.queryForList("SELECT (detail->>'count')::int AS count, (detail->>'completed')::boolean AS completed, "
            + "detail->>'error' AS error FROM audit_logs WHERE action = 'pii.reencrypt'")).singleElement()
            .satisfies(row -> assertThat(row).containsEntry("count", 2).containsEntry("completed", false)
                .containsEntry("error", DataAccessResourceFailureException.class.getName()));
    }
}
