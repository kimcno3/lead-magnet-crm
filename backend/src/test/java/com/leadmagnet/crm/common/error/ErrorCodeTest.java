package com.leadmagnet.crm.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void 상태_429는_too_many_requests로_묶는다() {
        assertThat(ErrorCode.forStatus(429)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.status().value()).isEqualTo(429);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.type()).isEqualTo("https://leadmagnet.example/problems/too_many_requests");
    }

    @Test
    void invalid_form_token은_422이고_다시_제출하라고_안내한다() {
        assertThat(ErrorCode.INVALID_FORM_TOKEN.status().value()).isEqualTo(422);
        assertThat(ErrorCode.INVALID_FORM_TOKEN.code()).isEqualTo("invalid_form_token");
        assertThat(ErrorCode.INVALID_FORM_TOKEN.defaultDetail()).contains("다시 제출해 주세요");
    }
}
