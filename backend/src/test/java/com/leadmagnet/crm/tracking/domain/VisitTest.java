package com.leadmagnet.crm.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.link.domain.AttributionChannel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitTest {

    static final UUID VISITOR = UUID.fromString("3f2b8c1e-9d4a-4e7b-8a6c-1b2d3e4f5a6b");
    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    @Test
    void referrer와_user_agent는_512자로_자른다() {
        Visit visit = Visit.record(1L, null, AttributionChannel.DIRECT, VISITOR, "r".repeat(513), "u".repeat(2000), false, NOW);
        assertThat(visit.getReferrer()).hasSize(512);
        assertThat(visit.getUserAgent()).hasSize(512);
    }

    @Test
    void 짧은_값과_null은_그대로_둔다() {
        Visit visit = Visit.record(1L, 7L, AttributionChannel.X, VISITOR, "https://t.co/a", null, true, NOW);
        assertThat(visit.getReferrer()).isEqualTo("https://t.co/a");
        assertThat(visit.getUserAgent()).isNull();
        assertThat(visit.getLinkId()).isEqualTo(7L);
        assertThat(visit.getChannel()).isEqualTo(AttributionChannel.X);
        assertThat(visit.getVisitorId()).isEqualTo(VISITOR);
        assertThat(visit.getOccurredAt()).isEqualTo(NOW);
        assertThat(visit.isBot()).isTrue();
    }

    @Test
    void 빈_문자열은_null로_저장한다() {
        Visit visit = Visit.record(1L, null, AttributionChannel.DIRECT, VISITOR, "", "", false, NOW);
        assertThat(visit.getReferrer()).isNull();
        assertThat(visit.getUserAgent()).isNull();
    }
}
