package com.leadmagnet.crm.form.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게시와 새 버전 업로드가 동시에 일어나도 폼의 published_html과 field_config는 같은 버전에서 온다 (ADR 0018).
 * 잠금 순서(템플릿 → 폼)가 지켜지는지 한 쪽 트랜잭션을 연 채로 다른 쪽이 기다리는지로 확인한다.
 */
@IntegrationTest
class PublishSnapshotConcurrencyTest {

    static final String V1 = """
        <!doctype html><html><body><form>
        <label>이름 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone" required></label></form></body></html>
        """;
    static final String V2 = """
        <!doctype html><html><body><form>
        <label>이름 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone" required></label>
        <label>메모 <input name="memo"></label></form></body></html>
        """;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    FormService formService;

    @Autowired
    TemplateVersionService versionService;

    @Autowired
    PlatformTransactionManager transactionManager;

    String token;
    ExecutorService executor = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 새_버전_업로드가_진행_중이면_게시는_기다렸다가_새_내용과_새_필드를_한_쌍으로_스냅샷한다() throws Exception {
        int form = AdminFixtures.createDraftForm(token, "race-a", V1);
        long template = templateIdOf(form);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Future<?> publish = tx.execute(status -> {
            versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8));
            Future<?> waiting = executor.submit(() -> formService.update(form, null, null, null, "published"));
            assertStillWaiting(waiting);
            return waiting;
        });
        publish.get(10, TimeUnit.SECONDS);

        assertSnapshotMatchesFields(form, V2, List.of("name", "phone", "memo"));
    }

    @Test
    void 게시가_진행_중이면_새_버전_업로드는_기다리고_게시된_폼은_옛_스냅샷_그대로다() throws Exception {
        int form = AdminFixtures.createDraftForm(token, "race-b", V1);
        long template = templateIdOf(form);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Future<?> upload = tx.execute(status -> {
            formService.update(form, null, null, null, "published");
            Future<?> waiting = executor.submit(() -> versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8)));
            assertStillWaiting(waiting);
            return waiting;
        });
        upload.get(10, TimeUnit.SECONDS);

        assertSnapshotMatchesFields(form, V1, List.of("name", "phone"));
        assertThat(jdbc.queryForObject("SELECT version FROM html_templates WHERE id = ?", Integer.class, template)).isEqualTo(2);
    }

    @Test
    void 게시와_새_버전_업로드를_동시에_반복해도_스냅샷과_필드는_항상_같은_버전이다() throws Exception {
        for (int i = 0; i < 15; i++) {
            DatabaseCleaner.truncateAll(jdbc);
            operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
            token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
            int form = AdminFixtures.createDraftForm(token, "race-" + i, V1);
            long template = templateIdOf(form);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> publish = executor.submit(() -> {
                start.await();
                return formService.update(form, null, null, null, "published");
            });
            Future<?> upload = executor.submit(() -> {
                start.await();
                return versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8));
            });
            start.countDown();
            publish.get(10, TimeUnit.SECONDS);
            upload.get(10, TimeUnit.SECONDS);

            String html = jdbc.queryForObject("SELECT published_html FROM forms WHERE id = ?", String.class, form);
            assertSnapshotMatchesFields(form, html, html.equals(V2) ? List.of("name", "phone", "memo") : List.of("name", "phone"));
        }
    }

    private void assertStillWaiting(Future<?> waiting) {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(waiting.isDone()).as("잠금을 기다려야 한다").isFalse();
    }

    private void assertSnapshotMatchesFields(int form, String expectedHtml, List<String> expectedFieldNames) {
        assertThat(jdbc.queryForObject("SELECT status FROM forms WHERE id = ?", String.class, form)).isEqualTo("published");
        assertThat(jdbc.queryForObject("SELECT published_html FROM forms WHERE id = ?", String.class, form)).isEqualTo(expectedHtml);
        assertThat(jdbc.queryForObject("SELECT published_template_sha256 FROM forms WHERE id = ?", String.class, form))
            .isEqualTo(com.leadmagnet.crm.template.api.TemplateVersionApiTest.sha256(expectedHtml));
        List<String> names = new ArrayList<>(jdbc.queryForList(
            "SELECT e->>'name' FROM forms, jsonb_array_elements(field_config) e WHERE id = ?", String.class, form));
        assertThat(names).containsExactlyElementsOf(expectedFieldNames);
    }

    private long templateIdOf(int form) {
        return jdbc.queryForObject("SELECT template_id FROM forms WHERE id = ?", Long.class, form);
    }
}
