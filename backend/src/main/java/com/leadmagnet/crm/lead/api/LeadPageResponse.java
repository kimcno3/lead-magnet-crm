package com.leadmagnet.crm.lead.api;

import com.leadmagnet.crm.lead.application.LeadQueryService.LeadPage;
import java.util.List;

public record LeadPageResponse(List<LeadResponse> items, int page, int size, long totalElements, int totalPages) {

    public static LeadPageResponse from(LeadPage page) {
        return new LeadPageResponse(page.items().stream().map(LeadResponse::from).toList(), page.page(), page.size(),
            page.totalElements(), page.totalPages());
    }
}
