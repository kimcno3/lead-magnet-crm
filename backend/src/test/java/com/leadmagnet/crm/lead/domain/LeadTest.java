package com.leadmagnet.crm.lead.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeadTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant LATER = NOW.plusSeconds(86_400);
    static final UUID VISITOR = UUID.fromString("7d1f6a52-2f2e-4b8a-9d0c-1a2b3c4d5e6f");

    static Lead lead() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("company", "하늘상사");
        payload.put("interests", List.of("a", "b"));
        Submission submission = new Submission("홍길동", "01012345678", "hong@example.com", payload);
        ProtectedContact contact = new ProtectedContact(new byte[] {1}, new byte[] {2}, new byte[] {3}, "p".repeat(64),
            "e".repeat(64));
        return Lead.receive(10L, 20L, new Attribution(30L, AttributionChannel.INSTAGRAM), VISITOR, submission, contact,
            ConsentEvidence.of("개인정보 수집에 동의합니다"), NOW);
    }

    @Test
    void 접수하면_동의_문구와_sha256을_기록한다() {
        Lead lead = lead();

        assertThat(lead.getConsentText()).isEqualTo("개인정보 수집에 동의합니다");
        assertThat(lead.getConsentSha256()).hasSize(64).isEqualTo(ConsentEvidence.sha256Hex("개인정보 수집에 동의합니다"));
        assertThat(lead.getConsentAt()).isEqualTo(NOW);
        assertThat(lead.isAnonymized()).isFalse();
    }

    @Test
    void 동의_문구_sha256은_UTF8_바이트의_소문자_hex다() throws Exception {
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest("동의".getBytes(StandardCharsets.UTF_8)));

        assertThat(ConsentEvidence.of("동의").sha256()).isEqualTo(expected);
    }

    @Test
    void 익명화하면_연락처_암호문과_해시를_지우고_payload는_키만_남긴다() {
        Lead lead = lead();

        lead.anonymize(LATER);

        assertThat(lead.isAnonymized()).isTrue();
        assertThat(lead.getAnonymizedAt()).isEqualTo(LATER);
        ProtectedContact contact = lead.getProtectedContact();
        assertThat(contact.nameEnc()).isNull();
        assertThat(contact.phoneEnc()).isNull();
        assertThat(contact.emailEnc()).isNull();
        assertThat(contact.phoneHash()).isNull();
        assertThat(contact.emailHash()).isNull();
        assertThat(lead.getPayload()).containsOnlyKeys("company", "interests");
        assertThat(lead.getPayload().get("company")).isNull();
        assertThat(lead.getPayload().get("interests")).isEqualTo(List.of());
    }

    @Test
    void 익명화해도_지표에_쓰는_값과_동의_증빙은_남는다() {
        Lead lead = lead();

        lead.anonymize(LATER);

        assertThat(lead.getFormId()).isEqualTo(10L);
        assertThat(lead.getCampaignId()).isEqualTo(20L);
        assertThat(lead.getLinkId()).isEqualTo(30L);
        assertThat(lead.getChannel()).isEqualTo(AttributionChannel.INSTAGRAM);
        assertThat(lead.getVisitorId()).isEqualTo(VISITOR);
        assertThat(lead.getCreatedAt()).isEqualTo(NOW);
        assertThat(lead.getConsentAt()).isEqualTo(NOW);
        assertThat(lead.getConsentText()).isEqualTo("개인정보 수집에 동의합니다");
    }

    @Test
    void 이미_익명화된_신청을_다시_익명화해도_처음_시각을_유지한다() {
        Lead lead = lead();
        lead.anonymize(LATER);

        boolean changed = lead.anonymize(LATER.plusSeconds(60));

        assertThat(changed).isFalse();
        assertThat(lead.getAnonymizedAt()).isEqualTo(LATER);
    }
}
