package com.leadmagnet.crm.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import java.util.Map;

/** 테스트에서 로그인해 Bearer access token을 얻는다. RestAssured.port를 먼저 설정해야 한다. */
public final class AdminLogin {

    private AdminLogin() {
    }

    public static String accessToken(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
            .when().post("/api/admin/auth/login")
            .then().statusCode(200).extract().path("accessToken");
    }
}
