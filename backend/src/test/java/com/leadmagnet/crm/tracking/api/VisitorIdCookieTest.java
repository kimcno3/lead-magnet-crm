package com.leadmagnet.crm.tracking.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitorIdCookieTest {

    @Test
    void 정규_형식의_UUID_v4만_방문자_id로_받는다() {
        assertThat(VisitorIdCookie.parse("3f2b8c1e-9d4a-4e7b-8a6c-1b2d3e4f5a6b"))
            .contains(UUID.fromString("3f2b8c1e-9d4a-4e7b-8a6c-1b2d3e4f5a6b"));
        assertThat(VisitorIdCookie.parse("3F2B8C1E-9D4A-4E7B-8A6C-1B2D3E4F5A6B")).isPresent();
    }

    @Test
    void 형식이_틀린_값은_받지_않는다() {
        assertThat(VisitorIdCookie.parse(null)).isEmpty();
        assertThat(VisitorIdCookie.parse("")).isEmpty();
        assertThat(VisitorIdCookie.parse("1-1-1-1-1")).isEmpty();
        assertThat(VisitorIdCookie.parse("3f2b8c1e-9d4a-1e7b-8a6c-1b2d3e4f5a6b")).isEmpty();
        assertThat(VisitorIdCookie.parse("3f2b8c1e-9d4a-4e7b-cа6c-1b2d3e4f5a6b")).isEmpty();
        assertThat(VisitorIdCookie.parse("3f2b8c1e-9d4a-4e7b-8a6c-1b2d3e4f5a6b0")).isEmpty();
        assertThat(VisitorIdCookie.parse(" 3f2b8c1e-9d4a-4e7b-8a6c-1b2d3e4f5a6b")).isEmpty();
    }
}
