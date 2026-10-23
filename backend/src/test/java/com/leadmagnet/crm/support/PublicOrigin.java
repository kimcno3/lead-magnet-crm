package com.leadmagnet.crm.support;

import com.leadmagnet.crm.common.web.PublicPort;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * public 커넥터로 보내는 요청. RestAssured.port는 admin 포트(@LocalServerPort)로 두고,
 * 공개 출처 요청은 이 헬퍼로 포트를 바꿔 보낸다. 리다이렉트는 따라가지 않는다.
 */
public final class PublicOrigin {

    private PublicOrigin() {
    }

    public static RequestSpecification given(PublicPort port) {
        return RestAssured.given().port(port.get()).redirects().follow(false);
    }
}
