package com.leadmagnet.crm.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.common.web.PublicPort;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공개 출처를 방문하는 사람 한 명. 브라우저처럼 lm_vid 쿠키를 들고 다니되, 리다이렉트는 직접 따라가
 * 단계마다 응답을 확인할 수 있게 한다. 방문자마다 새 인스턴스를 만든다.
 */
public final class PublicVisitor {

    public static final String COOKIE = "lm_vid";

    private final PublicPort port;
    private String visitorId;

    public PublicVisitor(PublicPort port) {
        this.port = port;
    }

    /** 이 방문자의 쿠키가 실린 public 포트 요청. */
    public RequestSpecification request() {
        RequestSpecification spec = PublicOrigin.given(port);
        return visitorId == null ? spec : spec.cookie(COOKIE, visitorId);
    }

    public String visitorId() {
        return visitorId;
    }

    /** /l/{code}로 가 302를 확인하고 Location(/f/{slug}?lm={code})을 돌려준다. 쿠키가 오면 기억한다. */
    public String openShortLink(String code) {
        Response response = request().get("/l/{code}", code);
        response.then().statusCode(302);
        remember(response);
        return response.getHeader("Location");
    }

    /** 단축 URL을 열고 Location을 그대로 따라가 폼 페이지를 방문한다(방문 한 건이 기록된다). */
    public Response visitViaShortLink(String code) {
        String location = openShortLink(code);
        // Location은 상대 경로이거나 PUBLIC_BASE_URL 절대 경로일 수 있어 경로+쿼리만 쓴다.
        String pathAndQuery = location.replaceFirst("^https?://[^/]+", "");
        assertThat(pathAndQuery).startsWith("/f/").endsWith("?lm=" + code);
        Response page = request().get(pathAndQuery);
        remember(page);
        return page;
    }

    public Response visitForm(String slug) {
        Response page = request().get("/f/{slug}", slug);
        remember(page);
        return page;
    }

    /** 래퍼가 하듯 JSON으로 제출한다. Origin은 공개 출처로 맞춘다. */
    public Response submit(String slug, Map<String, Object> fields, boolean consent, String lm) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fields", fields);
        body.put("consent", consent);
        if (lm != null) {
            body.put("lm", lm);
        }
        return request().contentType(ContentType.JSON).header("Origin", "http://localhost:" + port.get())
            .body(body).post("/api/public/forms/{slug}/submissions", slug);
    }

    private void remember(Response response) {
        String issued = response.getCookie(COOKIE);
        if (issued != null) {
            visitorId = issued;
        }
    }
}
