package com.leadmagnet.crm.support;

import com.leadmagnet.crm.common.web.PublicPort;
import io.restassured.response.Response;
import java.time.Duration;

/**
 * 제출에 실을 작성 시간 토큰(ft)을 래퍼처럼 {@code GET /api/public/forms/{slug}}로 받는다 (ADR 0019).
 * 발급 후 2초가 지나야 쓸 수 있으므로 sleep하지 않고 시계를 {@link #AGE}만큼 뒤로 돌린 채 발급받은 뒤 되돌린다.
 * 시계 오프셋은 그대로 0으로 돌아오므로 다른 테스트에 영향이 없다.
 */
public final class FormTokens {

    /** 받은 토큰의 나이. 최소 작성 시간(2초)보다 넉넉하게 둔다. */
    public static final Duration AGE = Duration.ofSeconds(3);

    private FormTokens() {
    }

    /** 바로 제출할 수 있는 토큰. 폼이 공개 중이 아니면(404·410) null이다. */
    public static String mature(PublicPort port, MutableClock clock, String slug) {
        clock.advance(AGE.negated());
        try {
            Response response = PublicOrigin.given(port).get("/api/public/forms/{slug}", slug);
            return response.statusCode() == 200 ? response.path("ft") : null;
        } finally {
            clock.advance(AGE);
        }
    }
}
