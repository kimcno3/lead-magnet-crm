package com.leadmagnet.crm.creator.api;

import com.leadmagnet.crm.creator.domain.Creator;
import java.time.Instant;

public record CreatorResponse(Long id, String name, String handle, String memo, String status, Instant createdAt,
        Instant updatedAt) {

    public static CreatorResponse from(Creator c) {
        return new CreatorResponse(c.getId(), c.getName(), c.getHandle(), c.getMemo(), c.getStatus().value(),
            c.getCreatedAt(), c.getUpdatedAt());
    }
}
