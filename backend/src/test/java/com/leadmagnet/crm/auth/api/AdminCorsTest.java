package com.leadmagnet.crm.auth.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** 관리자 API CORS는 ADMIN_ORIGIN 하나만 허용한다. */
@IntegrationTest
class AdminCorsTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 관리자_출처의_프리플라이트는_허용한다() {
        given()
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type")
            .when().options("/api/admin/auth/login")
            .then().statusCode(200)
            .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"))
            .header("Access-Control-Allow-Credentials", equalTo("true"))
            .header("Access-Control-Allow-Methods", containsString("POST"));
    }

    @Test
    void 다른_출처의_프리플라이트는_403으로_거부한다() {
        given()
            .header("Origin", "http://evil.example")
            .header("Access-Control-Request-Method", "POST")
            .when().options("/api/admin/auth/login")
            .then().statusCode(403)
            .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void 허용하지_않은_메서드의_프리플라이트는_거부한다() {
        given()
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "PUT")
            .when().options("/api/admin/auth/login")
            .then().statusCode(403);
    }
}
