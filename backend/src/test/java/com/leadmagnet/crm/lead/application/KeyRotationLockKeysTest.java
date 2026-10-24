package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 파기·재암호화·HMAC 교체는 서로 다른 advisory lock 키를 쓴다(한쪽이 다른 쪽을 막지 않게, ADR 0020·0022). */
class KeyRotationLockKeysTest {

    @Test
    void 세_배치의_잠금_키가_모두_다르다() {
        assertThat(new java.util.HashSet<>(java.util.List.of(LeadRetentionService.LOCK_KEY, PiiReencryptionService.LOCK_KEY,
            LeadHashRotation.LOCK_KEY))).hasSize(3);
    }
}
