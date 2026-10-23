package com.leadmagnet.crm.support;

import static io.restassured.config.HeaderConfig.headerConfig;

import com.leadmagnet.crm.common.web.PublicPort;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * public 커넥터로 보내는 요청. RestAssured.port는 admin 포트(@LocalServerPort)로 두고,
 * 공개 출처 요청은 이 헬퍼로 포트를 바꿔 보낸다. 리다이렉트는 따라가지 않는다.
 * 방문자는 사람 브라우저이므로 기본 User-Agent를 브라우저 값으로 둔다(HTTP 라이브러리 UA는 봇 방문으로 잡힌다, ADR 0019).
 * 테스트가 User-Agent를 다시 주면 그 값으로 바뀐다.
 */
public final class PublicOrigin {

    public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private PublicOrigin() {
    }

    public static RequestSpecification given(PublicPort port) {
        return RestAssured.given()
            .config(RestAssured.config().headerConfig(
                headerConfig().overwriteHeadersWithName("User-Agent", "Content-Type", "Accept")))
            .header("User-Agent", BROWSER_USER_AGENT)
            .port(port.get()).redirects().follow(false);
    }
}
