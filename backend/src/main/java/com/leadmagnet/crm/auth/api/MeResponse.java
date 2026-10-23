package com.leadmagnet.crm.auth.api;

import com.leadmagnet.crm.auth.application.CurrentOperator;

public record MeResponse(Long id, String email) {

    static MeResponse from(CurrentOperator operator) {
        return new MeResponse(operator.id(), operator.email());
    }
}
