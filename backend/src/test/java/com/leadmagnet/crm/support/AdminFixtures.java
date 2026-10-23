package com.leadmagnet.crm.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** admin API로 캠페인·템플릿·폼·링크를 만든다. RestAssured.port를 admin 포트로 먼저 설정해야 한다. */
public final class AdminFixtures {

    public static final String FORM_HTML = """
        <!doctype html><html><head><title>t</title></head><body><form>
        <label>이름 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone" required></label>
        <button>신청</button></form></body></html>
        """;

    private AdminFixtures() {
    }

    /** 캠페인과 템플릿을 새로 만들고 그 위에 draft 폼을 만든다. */
    public static int createDraftForm(String token, String slug) {
        int campaign = authed(token).body(Map.of("name", "캠페인 " + slug)).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
        int template = given().auth().oauth2(token)
            .multiPart("file", "t.html", FORM_HTML.getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");
        return authed(token)
            .body(Map.of("templateId", template, "title", "봄 특강 신청", "consentText", "개인정보 수집에 동의합니다",
                "slug", slug))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(201).extract().path("id");
    }

    public static int createPublishedForm(String token, String slug) {
        int formId = createDraftForm(token, slug);
        changeStatus(token, formId, "published");
        return formId;
    }

    public static void changeStatus(String token, int formId, String status) {
        authed(token).body(Map.of("status", status)).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
    }

    public static String createLink(String token, int formId, String channel) {
        return authed(token).body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(201).extract().path("code");
    }

    private static RequestSpecification authed(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }
}
