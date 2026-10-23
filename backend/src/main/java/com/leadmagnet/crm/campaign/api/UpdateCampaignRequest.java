package com.leadmagnet.crm.campaign.api;

import java.time.Instant;

/** 없는(null) 필드는 바꾸지 않는다. status는 "archived"만 받는다. */
public record UpdateCampaignRequest(String name, String description, Instant startsAt, Instant endsAt,
        String status) {
}
