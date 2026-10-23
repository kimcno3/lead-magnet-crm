package com.leadmagnet.crm.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditActorTest {

    @Test
    void user_agent는_512자로_자른다() {
        AuditActor actor = new AuditActor(1L, "203.0.113.7", "a".repeat(600));

        assertThat(actor.userAgent()).hasSize(512);
        assertThat(actor.ip()).isEqualTo("203.0.113.7");
    }

    @Test
    void ip는_45자로_자르고_빈_값은_null로_둔다() {
        assertThat(new AuditActor(null, "1".repeat(60), " ").ip()).hasSize(45);
        assertThat(new AuditActor(null, "", "  ").userAgent()).isNull();
        assertThat(new AuditActor(null, null, null).ip()).isNull();
    }

    @Test
    void 운영자_id를_붙인_사본을_만든다() {
        AuditActor anonymous = new AuditActor(null, "203.0.113.7", "curl");

        assertThat(anonymous.withOperator(7L)).isEqualTo(new AuditActor(7L, "203.0.113.7", "curl"));
    }
}
