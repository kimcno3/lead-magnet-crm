package com.leadmagnet.crm.link.api;

/** creatorId가 없으면 크리에이터 없는(운영자 자체) 링크다. */
public record CreateLinkRequest(String channel, Long creatorId) {
}
