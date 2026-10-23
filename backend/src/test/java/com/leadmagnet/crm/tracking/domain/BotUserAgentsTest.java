package com.leadmagnet.crm.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BotUserAgentsTest {

    final BotUserAgents defaults = new BotUserAgents(BotUserAgents.DEFAULT_PATTERNS);

    @ParameterizedTest
    @ValueSource(strings = {
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        "Twitterbot/1.0",
        "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
        "Mozilla/5.0 (compatible; kakaotalk-scrap/1.0; +https://devtalk.kakao.com/)",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36",
        "curl/8.4.0",
        "python-requests/2.31.0",
        "Java-http-client/21.0.2",
        "okhttp/4.12.0",
        "Apache-HttpClient/4.5.14 (Java/21.0.2)",
        "Java/1.8.0_392",
    })
    void 기본_패턴의_크롤러와_스크립트는_봇이다(String userAgent) {
        assertThat(defaults.isBot(userAgent)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 KAKAOTALK 10.4.5",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 300.0",
    })
    void 사람이_쓰는_브라우저와_인앱_브라우저는_봇이_아니다(String userAgent) {
        assertThat(defaults.isBot(userAgent)).isFalse();
    }

    @Test
    void 대소문자를_가리지_않는다() {
        assertThat(defaults.isBot("GOOGLEBOT")).isTrue();
        assertThat(new BotUserAgents(List.of("MyCrawler")).isBot("x mycrawler/1")).isTrue();
    }

    @Test
    void User_Agent가_없거나_비면_봇으로_본다() {
        assertThat(defaults.isBot(null)).isTrue();
        assertThat(defaults.isBot("  ")).isTrue();
    }

    @Test
    void 설정한_패턴만_쓰고_빈_패턴은_무시한다() {
        BotUserAgents custom = new BotUserAgents(List.of("acme-preview", " ", ""));

        assertThat(custom.isBot("acme-preview/2.0")).isTrue();
        assertThat(custom.isBot("Googlebot/2.1")).isFalse();
        assertThat(custom.isBot("Mozilla/5.0 Chrome/120")).isFalse();
    }
}
