package com.leadmagnet.crm.link.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkPropertiesTest {

    @Test
    void 끝의_슬래시는_떼고_나머지는_그대로_둔다() {
        assertThat(new LinkProperties("https://go.example.com/").baseUrl()).isEqualTo("https://go.example.com");
        assertThat(new LinkProperties("https://go.example.com").baseUrl()).isEqualTo("https://go.example.com");
        assertThat(new LinkProperties("http://localhost:8081").baseUrl()).isEqualTo("http://localhost:8081");
    }
}
