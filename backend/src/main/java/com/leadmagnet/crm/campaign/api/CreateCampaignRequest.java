package com.leadmagnet.crm.campaign.api;

import java.time.Instant;

public record CreateCampaignRequest(String name, String description, Instant startsAt, Instant endsAt) {
}
