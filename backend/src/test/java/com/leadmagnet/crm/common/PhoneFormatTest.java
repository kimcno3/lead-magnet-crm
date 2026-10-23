package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 관리자 화면 formatPhone과 같은 규칙: 숫자만 있는 국내 번호에 하이픈을 넣는다. */
class PhoneFormatTest {

    @Test
    void 휴대전화는_3_4_4로_끊는다() {
        assertThat(PhoneFormat.hyphenate("01012345678")).isEqualTo("010-1234-5678");
    }

    @Test
    void 구형_10자리_휴대전화는_3_3_4로_끊는다() {
        assertThat(PhoneFormat.hyphenate("0111234567")).isEqualTo("011-123-4567");
    }

    @Test
    void 서울_지역번호는_2자리로_끊는다() {
        assertThat(PhoneFormat.hyphenate("0212345678")).isEqualTo("02-1234-5678");
        assertThat(PhoneFormat.hyphenate("021234567")).isEqualTo("02-123-4567");
    }

    @Test
    void 이미_하이픈이_있으면_다시_맞춘다() {
        assertThat(PhoneFormat.hyphenate("010-1234-5678")).isEqualTo("010-1234-5678");
    }

    @Test
    void 형식을_알_수_없으면_그대로_두고_null은_null이다() {
        assertThat(PhoneFormat.hyphenate("+821012345678")).isEqualTo("+821012345678");
        assertThat(PhoneFormat.hyphenate("1234")).isEqualTo("1234");
        assertThat(PhoneFormat.hyphenate(null)).isNull();
        assertThat(PhoneFormat.hyphenate("")).isEmpty();
    }
}
