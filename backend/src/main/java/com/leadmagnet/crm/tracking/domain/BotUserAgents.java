package com.leadmagnet.crm.tracking.domain;

import java.util.List;
import java.util.Locale;

/**
 * User-Agent로 봇 방문을 가린다 (ADR 0019). 패턴은 대소문자를 가리지 않는 부분 문자열이다.
 * 링크 미리보기 크롤러·검색 크롤러·스크립트가 방문 지표를 부풀리지 않게 하려는 것이라 오탐(사람을 봇으로)보다
 * 미탐을 감수한다: 흔한 단어("bot" 하나 등)는 넣지 않는다. User-Agent가 없거나 비면 봇으로 본다(브라우저는 항상 보낸다).
 */
public final class BotUserAgents {

    /** 기본 패턴. {@code app.tracking.bot-user-agents}로 통째로 바꿀 수 있다. */
    public static final List<String> DEFAULT_PATTERNS = List.of(
        "googlebot", "bingbot", "yandexbot", "baiduspider", "duckduckbot", "applebot", "yeti", "daumoa",
        "facebookexternalhit", "facebot", "twitterbot", "slackbot", "slack-imgproxy", "linkedinbot", "discordbot",
        "telegrambot", "whatsapp", "kakaotalk-scrap", "headless", "curl/", "wget/", "python-requests",
        "python-urllib", "go-http-client", "libwww-perl", "httpie");

    private final List<String> patterns;

    public BotUserAgents(List<String> patterns) {
        this.patterns = patterns.stream()
            .filter(p -> p != null && !p.isBlank())
            .map(p -> p.trim().toLowerCase(Locale.ROOT))
            .toList();
    }

    public boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return patterns.stream().anyMatch(ua::contains);
    }
}
