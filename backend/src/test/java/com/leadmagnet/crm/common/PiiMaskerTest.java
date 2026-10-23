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

    @Test
    void 화면용_이름은_첫_글자와_마지막_글자_사이를_가린다() {
        assertThat(PiiMasker.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(PiiMasker.maskName("남궁민수")).isEqualTo("남*수");
        assertThat(PiiMasker.maskName("에이")).isEqualTo("에*");
        assertThat(PiiMasker.maskName("비")).isEqualTo("*");
        assertThat(PiiMasker.maskName(" 홍길동 ")).isEqualTo("홍*동");
        assertThat(PiiMasker.maskName(null)).isNull();
        assertThat(PiiMasker.maskName("")).isNull();
    }

    @Test
    void 화면용_전화번호는_앞_세_자리와_끝_네_자리만_남긴다() {
        assertThat(PiiMasker.maskPhoneForDisplay("01012345678")).isEqualTo("010-****-5678");
        assertThat(PiiMasker.maskPhoneForDisplay("0161234567")).isEqualTo("016-****-4567");
        assertThat(PiiMasker.maskPhoneForDisplay("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(PiiMasker.maskPhoneForDisplay("1234567")).isEqualTo("****");
        assertThat(PiiMasker.maskPhoneForDisplay(null)).isNull();
    }

    @Test
    void 화면용_이메일은_첫_글자와_도메인만_남기고_없으면_null이다() {
        assertThat(PiiMasker.maskEmailForDisplay("hong@example.com")).isEqualTo("h***@example.com");
        assertThat(PiiMasker.maskEmailForDisplay(null)).isNull();
    }
}
