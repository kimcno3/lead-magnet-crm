package com.leadmagnet.crm.tracking.config;

import com.leadmagnet.crm.tracking.domain.BotUserAgents;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 봇 방문 User-Agent 패턴 (ADR 0019). 설정하지 않으면 {@link BotUserAgents#DEFAULT_PATTERNS}.
 * 설정하면 기본 목록을 대신한다(합치지 않는다).
 */
@ConfigurationProperties(prefix = "app.tracking")
public record BotDetectionProperties(List<String> botUserAgents) {

    public BotDetectionProperties {
        botUserAgents = botUserAgents == null || botUserAgents.isEmpty()
            ? BotUserAgents.DEFAULT_PATTERNS : List.copyOf(botUserAgents);
    }
}
