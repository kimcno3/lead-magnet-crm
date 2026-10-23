package com.leadmagnet.crm.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttributionChannelTest {

    @Test
    void 링크_채널은_같은_이름의_귀속_채널로_바뀌고_값은_소문자다() {
        for (Channel c : Channel.values()) {
            assertThat(AttributionChannel.of(c).value()).isEqualTo(c.value());
        }
        assertThat(AttributionChannel.direct().value()).isEqualTo("direct");
        assertThat(AttributionChannel.fromValue("direct")).isEqualTo(AttributionChannel.DIRECT);
        assertThat(AttributionChannel.fromValue("threads")).isEqualTo(AttributionChannel.THREADS);
    }

    @Test
    void 귀속_결과는_링크가_있으면_링크_id와_채널을_없으면_direct를_가진다() {
        DistributionLink link = DistributionLink.create(1L, Channel.X, "abCD1234", "s", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 42L);

        assertThat(Attribution.of(link)).isEqualTo(new Attribution(42L, AttributionChannel.X));
        assertThat(Attribution.direct()).isEqualTo(new Attribution(null, AttributionChannel.DIRECT));
    }
}
