package com.leadmagnet.crm.form.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.form.domain.Form;
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
 * 한 쪽 트랜잭션을 연 채로 다른 쪽이 기다리는지로 "같은 템플릿 행을 두고 직렬화된다"를 확인한다.
 * 이 테스트는 잠금 순서(템플릿 → 폼)가 맞는지, 곧 교착이 없는지는 가려내지 못한다(폼 먼저 잠그는 구현도
 * 같은 대기를 보인다). 교착 없음은 코드로 논증한다: 템플릿을 바꾸거나 폼을 새 스냅샷으로 만드는 경로는
 * {@code TemplateVersionService.upload}, {@code FormService.update}, {@code FormService.create} 셋이고
 * 모두 템플릿 행을 먼저 잠근 뒤에만 폼 행을 잠근다(ADR 0018).
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

    static final com.leadmagnet.crm.common.web.AuditActor ACTOR =
        new com.leadmagnet.crm.common.web.AuditActor(null, "127.0.0.1", "test");

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
            versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8), ACTOR);
            Future<?> waiting = executor.submit(() -> formService.update(form, null, null, null, "published", ACTOR));
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
            formService.update(form, null, null, null, "published", ACTOR);
            Future<?> waiting = executor.submit(() -> versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8), ACTOR));
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
                return formService.update(form, null, null, null, "published", ACTOR);
            });
            Future<?> upload = executor.submit(() -> {
                start.await();
                return versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8), ACTOR);
            });
            start.countDown();
            publish.get(10, TimeUnit.SECONDS);
            upload.get(10, TimeUnit.SECONDS);

            String html = jdbc.queryForObject("SELECT published_html FROM forms WHERE id = ?", String.class, form);
            assertSnapshotMatchesFields(form, html, html.equals(V2) ? List.of("name", "phone", "memo") : List.of("name", "phone"));
        }
    }

    @Test
    void 새_버전_업로드_두_건이_겹치면_두_번째는_기다렸다가_버전_3이고_이전_필드는_V2다() throws Exception {
        int form = AdminFixtures.createDraftForm(token, "race-d", V1);
        long template = templateIdOf(form);
        String v2 = V2.replace("이름 <input", "성함 <input");
        String v3 = V2.replace("이름 <input", "이름3 <input");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Future<?> second = tx.execute(status -> {
            versionService.upload(template, "v2.html", v2.getBytes(StandardCharsets.UTF_8), ACTOR);
            Future<?> waiting = executor.submit(() -> versionService.upload(template, "v3.html", v3.getBytes(StandardCharsets.UTF_8), ACTOR));
            assertStillWaiting(waiting);
            return waiting;
        });
        second.get(10, TimeUnit.SECONDS);

        assertThat(jdbc.queryForObject("SELECT version FROM html_templates WHERE id = ?", Integer.class, template)).isEqualTo(3);
        // V2를 이전 필드로 비교했으면 운영자가 건드리지 않은 label은 V3 값을 따른다(V1로 비교하면 V2 값이 남는다).
        assertThat(jdbc.queryForObject("SELECT field_config->0->>'label' FROM forms WHERE id = ?", String.class, form))
            .isEqualTo("이름3");
    }

    @Test
    void 새_버전_업로드가_진행_중이면_폼_생성은_기다렸다가_새_필드로_만들어진다() throws Exception {
        int draft = AdminFixtures.createDraftForm(token, "race-c", V1);
        long template = templateIdOf(draft);
        int campaign = jdbc.queryForObject("SELECT campaign_id FROM forms WHERE id = ?", Integer.class, draft);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Future<Form> creating = tx.execute(status -> {
            versionService.upload(template, "v2.html", V2.getBytes(StandardCharsets.UTF_8), ACTOR);
            Future<Form> waiting = executor.submit(
                () -> formService.create(campaign, template, "새 폼", "동의합니다", "race-c-new"));
            assertStillWaiting(waiting);
            return waiting;
        });
        long created = creating.get(10, TimeUnit.SECONDS).getId();
        formService.update(created, null, null, null, "published", ACTOR);

        assertSnapshotMatchesFields((int) created, V2, List.of("name", "phone", "memo"));
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
