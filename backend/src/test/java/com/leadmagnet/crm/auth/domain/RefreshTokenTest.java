package com.leadmagnet.crm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    static final Duration TTL = Duration.ofDays(14);

    @Test
    void 발급하면_원문이_아닌_SHA256_hex_해시를_저장하고_유효기간을_둔다() {
        RefreshToken token = RefreshToken.issue(1L, "raw-token", UUID.randomUUID(), NOW, TTL);

        assertThat(token.getTokenHash())
            .hasSize(64)
            .isEqualTo(RefreshToken.hashOf("raw-token"))
            .isNotEqualTo("raw-token")
            .matches("[0-9a-f]{64}");
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void 해시는_입력이_같으면_같고_다르면_다르다() {
        assertThat(RefreshToken.hashOf("a")).isEqualTo(RefreshToken.hashOf("a"));
        assertThat(RefreshToken.hashOf("a")).isNotEqualTo(RefreshToken.hashOf("b"));
        // SHA-256("abc") 테스트 벡터
        assertThat(RefreshToken.hashOf("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void 만료_시각이_지나면_만료다() {
        RefreshToken token = RefreshToken.issue(1L, "raw", UUID.randomUUID(), NOW, TTL);

        assertThat(token.isExpired(NOW.plus(TTL).minusSeconds(1))).isFalse();
        assertThat(token.isExpired(NOW.plus(TTL))).isTrue();
        assertThat(token.isExpired(NOW.plus(TTL).plusSeconds(1))).isTrue();
    }

    @Test
    void 폐기하면_폐기_시각이_남고_다시_폐기해도_처음_시각을_유지한다() {
        RefreshToken token = RefreshToken.issue(1L, "raw", UUID.randomUUID(), NOW, TTL);

        token.revoke(NOW.plusSeconds(10));
        token.revoke(NOW.plusSeconds(20));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
