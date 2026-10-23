package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 신청 접수(public 포트). 서버가 신뢰 경계라서 래퍼를 거치지 않은 직접 호출도 같은 규칙으로 막는다. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadSubmissionApiTest {

    static final String HTML = """
        <!doctype html><html><body><form>
        <label>이름 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone" required></label>
        <label>이메일 <input type="email" name="email"></label>
        <label>직업 <select name="job"><option value="student">학생</option><option value="worker">직장인</option></select></label>
        <label><input type="checkbox" name="interests" value="x">x</label>
        <label><input type="checkbox" name="interests" value="y">y</label>
        <label>메모 <textarea name="memo"></textarea></label>
        <button>신청</button></form></body></html>
        """;

    static final String PATH = "/api/public/forms/{slug}/submissions";
    static final String PHONE = "010-1234-5678";
    static final String PHONE_DIGITS = "01012345678";

    @LocalServerPort
    int adminPort;

    @Autowired
    PublicPort publicPort;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PiiCipher cipher;

    @Autowired
    PiiHasher hasher;

    String token;
    int formId;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        formId = AdminFixtures.createPublishedForm(token, "spring-2026", HTML);
    }

    static Map<String, Object> fields(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static Map<String, Object> body(Map<String, Object> fields, Object consent, String lm) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fields", fields);
        map.put("consent", consent);
        if (lm != null) {
            map.put("lm", lm);
        }
        return map;
    }

    RequestSpecification json() {
        return PublicOrigin.given(publicPort).contentType(ContentType.JSON);
    }

    Response submit(String slug, Map<String, Object> fields) {
        return json().body(body(fields, true, null)).post(PATH, slug);
    }

    List<Map<String, Object>> leads() {
        return jdbc.queryForList("SELECT * FROM leads ORDER BY id");
    }

    @Test
    void 정상_제출은_201과_received만_돌려주고_컬럼과_payload에_나눠_저장한다() {
        Response response = submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", "Kim@Example.com",
            "job", "student", "interests", List.of("x", "y"), "memo", ""));

        response.then().statusCode(201).body("status", equalTo("received")).body("size()", equalTo(1));
        assertThat(leads()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("form_id", (long) formId).containsEntry("name", "홍길동")
                .containsEntry("phone", PHONE_DIGITS).containsEntry("email", "kim@example.com")
                .containsEntry("channel", "direct").containsEntry("link_id", null)
                .containsEntry("visitor_id", null);
            assertThat(row.get("consent_at")).isNotNull();
            assertThat(row.get("campaign_id")).isEqualTo(
                jdbc.queryForObject("SELECT campaign_id FROM forms WHERE id = ?", Long.class, formId));
        });
        assertThat(jdbc.queryForList("SELECT jsonb_object_keys(payload) FROM leads", String.class))
            .containsExactlyInAnyOrder("job", "interests");
        assertThat(jdbc.queryForObject("SELECT payload->>'job' FROM leads", String.class)).isEqualTo("student");
        assertThat(jdbc.queryForList("SELECT jsonb_array_elements_text(payload->'interests') FROM leads", String.class))
            .containsExactly("x", "y");
    }

    @Test
    void 정상_제출은_평문과_함께_암호문과_해시도_저장한다() {
        submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", " Kim@Example.com")).then().statusCode(201);

        assertThat(leads()).singleElement().satisfies(row -> {
            assertThat(cipher.decrypt((byte[]) row.get("name_enc"))).isEqualTo("홍길동");
            assertThat(cipher.decrypt((byte[]) row.get("phone_enc"))).isEqualTo(PHONE_DIGITS);
            assertThat(cipher.decrypt((byte[]) row.get("email_enc"))).isEqualTo("kim@example.com");
            assertThat(row).containsEntry("phone_hash", hasher.hashPhone(PHONE_DIGITS))
                .containsEntry("email_hash", hasher.hashEmail("kim@example.com"));
        });
    }

    @Test
    void 이메일이_없으면_이메일_암호문과_해시도_비운다() {
        submit("spring-2026", fields("phone", PHONE, "name", "홍")).then().statusCode(201);

        assertThat(leads()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("email_enc", null).containsEntry("email_hash", null));
    }

    @Test
    void 이_폼의_링크_code를_lm으로_보내면_그_링크와_채널로_귀속한다() {
        String code = AdminFixtures.createLink(token, formId, "instagram");

        json().body(body(fields("name", "홍길동", "phone", PHONE), true, code)).post(PATH, "spring-2026")
            .then().statusCode(201);

        Long linkId = jdbc.queryForObject("SELECT id FROM distribution_links WHERE code = ?", Long.class, code);
        assertThat(leads()).singleElement().satisfies(row ->
            assertThat(row).containsEntry("channel", "instagram").containsEntry("link_id", linkId));
    }

    @Test
    void 다른_폼의_code나_모르는_lm은_direct로_귀속한다() {
        int other = AdminFixtures.createPublishedForm(token, "autumn-2026", HTML);
        String otherCode = AdminFixtures.createLink(token, other, "x");

        json().body(body(fields("name", "가", "phone", "010-1111-2222"), true, otherCode)).post(PATH, "spring-2026")
            .then().statusCode(201);
        json().body(body(fields("name", "나", "phone", "010-3333-4444"), true, "no-such-code!")).post(PATH, "spring-2026")
            .then().statusCode(201);

        assertThat(leads()).extracting(r -> r.get("channel")).containsExactly("direct", "direct");
        assertThat(leads()).extracting(r -> r.get("link_id")).containsOnlyNulls();
    }

    @Test
    void 유효한_lm_vid_쿠키는_visitor_id로_저장하고_응답에_쿠키를_발급하지_않는다() {
        String visitor = UUID.randomUUID().toString();

        Response response = json().cookie("lm_vid", visitor).body(body(fields("name", "홍", "phone", PHONE), true, null))
            .post(PATH, "spring-2026");

        response.then().statusCode(201);
        assertThat(response.getHeaders().hasHeaderWithName("Set-Cookie")).isFalse();
        assertThat(leads()).singleElement().satisfies(row ->
            assertThat(row.get("visitor_id")).isEqualTo(UUID.fromString(visitor)));
    }

    @Test
    void 형식이_틀린_lm_vid_쿠키는_visitor_id를_null로_둔다() {
        json().cookie("lm_vid", "not-a-uuid").body(body(fields("name", "홍", "phone", PHONE), true, null))
            .post(PATH, "spring-2026").then().statusCode(201);

        assertThat(leads()).singleElement().satisfies(row -> assertThat(row.get("visitor_id")).isNull());
    }

    @Test
    void 필수_필드가_없으면_422와_필드별_오류를_돌려주고_저장하지_않는다() {
        submit("spring-2026", fields("phone", PHONE)).then().statusCode(422)
            .contentType(containsString("application/problem+json"))
            .body("code", equalTo("validation_failed"))
            .body("errors.find { it.field == 'name' }.code", equalTo("required"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 형식이_틀린_전화번호와_이메일은_한_번에_모두_422다() {
        submit("spring-2026", fields("name", "홍", "phone", "02-123-4567", "email", "nope")).then().statusCode(422)
            .body("errors", hasSize(2))
            .body("errors.field", hasItem("phone")).body("errors.field", hasItem("email"))
            .body("errors.code", hasItem("invalid_format"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 폼에_없는_필드는_unknown_field_422다() {
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "role", "admin")).then().statusCode(422)
            .body("errors.find { it.field == 'role' }.code", equalTo("unknown_field"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 동의가_없거나_true가_아니면_422_consent_required이고_저장하지_않는다() {
        for (Object consent : new Object[] {false, null, "true", 1}) {
            json().body(body(fields("name", "홍", "phone", PHONE), consent, null)).post(PATH, "spring-2026")
                .then().statusCode(422).body("errors.find { it.field == 'consent' }.code", equalTo("consent_required"));
        }
        json().body(Map.of("fields", fields("name", "홍", "phone", PHONE))).post(PATH, "spring-2026")
            .then().statusCode(422).body("errors.code", hasItem("consent_required"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 전화번호와_이메일이_모두_비면_contact_required_422다() {
        String html = HTML.replace("name=\"phone\" required", "name=\"phone\"");

        AdminFixtures.createPublishedForm(token, "optional-contact", html);

        submit("optional-contact", fields("name", "홍")).then().statusCode(422)
            .body("errors.find { it.field == 'contact' }.code", equalTo("contact_required"));
        assertThat(leads()).isEmpty();
    }

    @Test
    void draft_폼이나_없는_slug는_404다() {
        AdminFixtures.createDraftForm(token, "draft-form", HTML);

        submit("draft-form", fields("name", "홍", "phone", PHONE)).then().statusCode(404).body("code", equalTo("not_found"));
        submit("no-such-form", fields("name", "홍", "phone", PHONE)).then().statusCode(404);
        assertThat(leads()).isEmpty();
    }

    @Test
    void closed_폼은_410이다() {
        AdminFixtures.changeStatus(token, formId, "closed");

        submit("spring-2026", fields("name", "홍", "phone", PHONE)).then().statusCode(410).body("code", equalTo("gone"));
        assertThat(leads()).isEmpty();
    }

    @Test
    void 같은_폼에_같은_전화번호는_표기가_달라도_409다() {
        submit("spring-2026", fields("name", "홍", "phone", PHONE)).then().statusCode(201);

        submit("spring-2026", fields("name", "다른사람", "phone", "01012345678", "email", "new@example.com"))
            .then().statusCode(409).contentType(containsString("application/problem+json"))
            .body("code", equalTo("duplicate_submission"));

        assertThat(leads()).hasSize(1);
    }

    @Test
    void 같은_폼에_대소문자만_다른_이메일은_409다() {
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "email", "kim@example.com")).then().statusCode(201);

        submit("spring-2026", fields("name", "김", "phone", "010-9999-8888", "email", "KIM@Example.COM"))
            .then().statusCode(409).body("code", equalTo("duplicate_submission"));

        assertThat(leads()).hasSize(1);
    }

    @Test
    void 평문이_달라도_해시가_같으면_해시_인덱스가_409로_막는다() {
        // 평문 인덱스에는 걸리지 않고 해시 인덱스에만 걸리는 행을 직접 넣는다(contract 이후 해시만 남은 상태와 같다).
        jdbc.update("INSERT INTO leads (form_id, campaign_id, channel, phone, email, phone_hash, email_hash, consent_at) "
            + "SELECT id, campaign_id, 'direct', '01000000001', 'legacy@example.com', ?, ?, now() FROM forms WHERE id = ?",
            hasher.hashPhone(PHONE_DIGITS), hasher.hashEmail("kim@example.com"), formId);

        submit("spring-2026", fields("name", "홍", "phone", PHONE)).then().statusCode(409)
            .body("code", equalTo("duplicate_submission"));
        submit("spring-2026", fields("name", "김", "phone", "010-5555-6666", "email", "KIM@example.COM"))
            .then().statusCode(409).body("code", equalTo("duplicate_submission"));

        assertThat(leads()).hasSize(1);
    }

    @Test
    void 다른_폼에는_같은_연락처로_다시_신청할_수_있다() {
        AdminFixtures.createPublishedForm(token, "autumn-2026", HTML);
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "email", "kim@example.com")).then().statusCode(201);

        submit("autumn-2026", fields("name", "홍", "phone", PHONE, "email", "kim@example.com")).then().statusCode(201);

        assertThat(leads()).hasSize(2);
    }

    @Test
    void 이메일이_없는_신청_둘은_서로_중복이_아니다() {
        submit("spring-2026", fields("name", "가", "phone", "010-1111-2222")).then().statusCode(201);
        submit("spring-2026", fields("name", "나", "phone", "010-3333-4444")).then().statusCode(201);

        assertThat(leads()).hasSize(2);
    }

    @Test
    void 같은_연락처를_동시에_제출하면_하나만_저장되고_나머지는_409다() throws Exception {
        int threads = 6;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    barrier.await();
                    return submit("spring-2026", fields("name", "홍", "phone", PHONE)).statusCode();
                };
                results.add(pool.submit(call));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(leads()).hasSize(1);
    }

    @Test
    void admin_포트로_제출하면_404다() {
        RestAssured.given().contentType(ContentType.JSON).body(body(fields("name", "홍", "phone", PHONE), true, null))
            .post(PATH, "spring-2026").then().statusCode(404);

        assertThat(leads()).isEmpty();
    }

    @Test
    void Content_Type이_JSON이_아니면_415이고_저장하지_않는다() {
        String raw = "{\"fields\":{\"name\":\"홍\",\"phone\":\"" + PHONE + "\"},\"consent\":true}";

        PublicOrigin.given(publicPort).contentType(ContentType.TEXT).body(raw).post(PATH, "spring-2026")
            .then().statusCode(415).contentType(containsString("application/problem+json"))
            .body("code", equalTo("unsupported_media_type"));
        PublicOrigin.given(publicPort).contentType(ContentType.URLENC).formParam("consent", "true")
            .post(PATH, "spring-2026").then().statusCode(415).body("code", equalTo("unsupported_media_type"));
        PublicOrigin.given(publicPort).body(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .post(PATH, "spring-2026").then().statusCode(415);

        assertThat(leads()).isEmpty();
    }

    @Test
    void Origin이_null이면_403_forbidden_origin이고_저장하지_않는다() {
        json().header("Origin", "null").body(body(fields("name", "홍", "phone", PHONE), true, null))
            .post(PATH, "spring-2026").then().statusCode(403).contentType(containsString("application/problem+json"))
            .body("code", equalTo("forbidden_origin"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 실제_출처의_Origin이나_Origin_없는_요청은_받는다() {
        json().header("Origin", "http://localhost:" + publicPort.get())
            .body(body(fields("name", "가", "phone", "010-1111-2222"), true, null)).post(PATH, "spring-2026")
            .then().statusCode(201);
        submit("spring-2026", fields("name", "나", "phone", "010-3333-4444")).then().statusCode(201);

        assertThat(leads()).hasSize(2);
    }

    @Test
    void 본문이_한도를_넘으면_413이고_저장하지_않는다() throws Exception {
        String big = "{\"fields\":{\"name\":\"홍\",\"phone\":\"" + PHONE + "\",\"memo\":\"" + "a".repeat(70 * 1024)
            + "\"},\"consent\":true}";

        json().body(big).post(PATH, "spring-2026").then().statusCode(413).contentType(containsString("application/problem+json"))
            .body("code", equalTo("payload_too_large"));
        // Content-Length 없이 chunked로 보내도 실제 읽은 양으로 막는다.
        java.net.http.HttpResponse<String> chunked = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    "http://localhost:" + publicPort.get() + "/api/public/forms/spring-2026/submissions"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofInputStream(
                    () -> new java.io.ByteArrayInputStream(big.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(chunked.statusCode()).isEqualTo(413);

        assertThat(leads()).isEmpty();
    }

    @Test
    void 필드가_51개면_too_many_fields_422_하나다() {
        Map<String, Object> many = fields("name", "홍", "phone", PHONE);
        for (int i = 0; i < 49; i++) {
            many.put("k" + i, "v");
        }

        submit("spring-2026", many).then().statusCode(422).body("errors", hasSize(1))
            .body("errors[0].code", equalTo("too_many_fields")).body("errors[0].field", equalTo("fields"));
        assertThat(leads()).isEmpty();
    }

    @Test
    void checkbox가_넘치거나_중복이면_422다() {
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "interests", List.of("x", "y", "x"))).then()
            .statusCode(422).body("errors[0].code", equalTo("invalid_value"));
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "interests", List.of("x", "x"))).then()
            .statusCode(422).body("errors[0].code", equalTo("invalid_value"));
        assertThat(leads()).isEmpty();
    }

    @Test
    void value가_없는_필수_checkbox는_브라우저_기본값_on으로_신청할_수_있다() {
        AdminFixtures.createPublishedForm(token, "agree-only", """
            <!doctype html><html><body><form>
            <label>전화 <input type="tel" name="phone" required></label>
            <label><input type="checkbox" name="agree" required> 약관 동의</label>
            <button>신청</button></form></body></html>
            """);

        submit("agree-only", fields("phone", PHONE, "agree", "on")).then().statusCode(201);

        assertThat(jdbc.queryForList("SELECT jsonb_array_elements_text(payload->'agree') FROM leads", String.class))
            .containsExactly("on");
    }

    @Test
    void checkbox_값_하나는_배열로_저장한다() {
        submit("spring-2026", fields("name", "홍", "phone", PHONE, "interests", "y")).then().statusCode(201);

        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(payload->'interests') FROM leads", String.class))
            .isEqualTo("array");
    }

    @Test
    void Origin이_null이면_깨진_본문이어도_403이다() {
        json().header("Origin", "null").body("{not json").post(PATH, "spring-2026").then().statusCode(403)
            .body("code", equalTo("forbidden_origin"));
    }

    @Test
    void 깨진_JSON은_400이다() {
        json().body("{not json").post(PATH, "spring-2026").then().statusCode(400)
            .body("code", equalTo("malformed_request"));

        assertThat(leads()).isEmpty();
    }

    @Test
    void 이름_이메일_payload에_NUL이_있으면_422_invalid_value이고_저장하지_않으며_로그에_값을_남기지_않는다(
            CapturedOutput output) {
        submit("spring-2026", fields("name", "홍\u0000길동", "phone", PHONE, "email", "secret.person@example.com"))
            .then().statusCode(422).body("errors.find { it.field == 'name' }.code", equalTo("invalid_value"));
        submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", "secret.\u0000person@example.com"))
            .then().statusCode(422).body("errors.find { it.field == 'email' }.code", equalTo("invalid_value"));
        submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", "secret.person@example.com",
            "memo", "비밀\u0000메모"))
            .then().statusCode(422).body("errors.find { it.field == 'memo' }.code", equalTo("invalid_value"));

        assertThat(leads()).isEmpty();
        assertThat(output.getAll())
            .doesNotContain(PHONE).doesNotContain(PHONE_DIGITS).doesNotContain("secret.person@example.com")
            .doesNotContain("비밀").doesNotContain("홍길동").doesNotContain("22P05").doesNotContain("Zero bytes");
    }

    @Test
    void 성공_응답과_로그에_전화번호_이메일_원문이_남지_않는다(CapturedOutput output) {
        submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", "secret.person@example.com"))
            .then().statusCode(201).body("status", notNullValue()).body("phone", nullValue());
        submit("spring-2026", fields("name", "홍길동", "phone", PHONE, "email", "other@example.com")).then().statusCode(409);
        submit("spring-2026", fields("name", "홍길동", "phone", "010-7777-6666", "email", "secret.person@example.com"))
            .then().statusCode(409);

        assertThat(output.getAll())
            .doesNotContain(PHONE).doesNotContain(PHONE_DIGITS).doesNotContain("010-7777-6666").doesNotContain("01077776666")
            .doesNotContain("secret.person@example.com").doesNotContain("other@example.com");
    }
}
