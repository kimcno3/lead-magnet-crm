package com.leadmagnet.crm.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LinkCodeTest {

    @Test
    void 생성한_코드는_base62_8자다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = LinkCode.generate();
            assertThat(code).matches("[0-9A-Za-z]{8}");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(990);
    }
}
