package com.leadmagnet.crm.tracking.config;

import com.leadmagnet.crm.tracking.domain.BotUserAgents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BotDetectionConfig {

    @Bean
    BotUserAgents botUserAgents(BotDetectionProperties properties) {
        return new BotUserAgents(properties.botUserAgents());
    }
}
