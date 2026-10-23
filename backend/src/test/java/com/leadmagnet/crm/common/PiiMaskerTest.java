package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void 이메일은_첫_글자와_도메인만_남긴다() {
        assertThat(PiiMasker.maskEmail("owner@example.com")).isEqualTo("o***@example.com");
        assertThat(PiiMasker.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(PiiMasker.maskEmail(null)).isEmpty();
    }

    @Test
    void 전화번호는_끝_네_자리만_남긴다() {
        assertThat(PiiMasker.maskPhone("010-1234-5678")).isEqualTo("***-****-5678");
        assertThat(PiiMasker.maskPhone("123")).isEqualTo("****");
        assertThat(PiiMasker.maskPhone("")).isEmpty();
    }
}
