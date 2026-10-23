package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PiiHasherTest {

    final PiiHasher hasher = new PiiHasher(PiiCipherTest.KEY);

    @Test
    void 같은_값과_키면_항상_같은_64자_hex다() {
        String a = hasher.hashPhone("01012345678");

        assertThat(a).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(new PiiHasher(PiiCipherTest.KEY.clone()).hashPhone("01012345678")).isEqualTo(a);
    }

    @Test
    void 알려진_HMAC_SHA256_값과_같다() {
        // 키 0x00..0x1f, 값 "abc"를 openssl로 계산한 값과 비교한다:
        // printf abc | openssl dgst -sha256 -mac HMAC -macopt hexkey:000102...1f
        assertThat(hasher.hash("abc"))
            .isEqualTo("f0133729c4163dede81e21cd47839256da58171238c8a0d874397c73b14e1e47");
    }

    @Test
    void 전화번호는_숫자만_남겨_해시한다() {
        assertThat(hasher.hashPhone("010-1234-5678")).isEqualTo(hasher.hashPhone("01012345678"));
    }

    @Test
    void 이메일은_앞뒤_공백을_자르고_소문자로_해시한다() {
        assertThat(hasher.hashEmail(" Kim@Example.COM ")).isEqualTo(hasher.hashEmail("kim@example.com"));
        assertThat(hasher.hashEmail("kim@example.com")).isNotEqualTo(hasher.hashEmail("kim2@example.com"));
    }

    @Test
    void 키가_다르면_해시도_다르다() {
        assertThat(new PiiHasher(PiiCipherTest.OTHER_KEY).hashPhone("01012345678"))
            .isNotEqualTo(hasher.hashPhone("01012345678"));
    }

    @Test
    void null이나_빈_값은_null이다() {
        assertThat(hasher.hashPhone(null)).isNull();
        assertThat(hasher.hashEmail(null)).isNull();
        assertThat(hasher.hashEmail("  ")).isNull();
        assertThat(hasher.hashPhone("--")).isNull();
    }

    @Test
    void 키가_32바이트가_아니면_만들_수_없다() {
        assertThatThrownBy(() -> new PiiHasher(new byte[31])).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32바이트");
    }
}
