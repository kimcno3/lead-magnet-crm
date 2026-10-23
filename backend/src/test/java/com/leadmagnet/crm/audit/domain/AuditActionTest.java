package com.leadmagnet.crm.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditActionTest {

    @Test
    void 값으로_찾고_모르는_값은_비어_있다() {
        assertThat(AuditAction.fromValue("lead.contact.view")).contains(AuditAction.LEAD_CONTACT_VIEW);
        assertThat(AuditAction.fromValue("lead.export.reveal")).contains(AuditAction.LEAD_EXPORT_REVEAL);
        assertThat(AuditAction.fromValue("retention.anonymize")).contains(AuditAction.RETENTION_ANONYMIZE);
        assertThat(AuditAction.fromValue("LEAD_LIST")).isEqualTo(Optional.empty());
        assertThat(AuditAction.fromValue(null)).isEmpty();
    }

    @Test
    void 모든_값은_40자_이하다() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(action.value()).hasSizeLessThanOrEqualTo(40);
        }
    }
}
