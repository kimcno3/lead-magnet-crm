package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.ProtectedContact;
import org.springframework.stereotype.Component;

/**
 * 신청자 연락처를 저장용(암호문·해시)으로 바꾸고 되돌린다 (ADR 0015). 암·복호화는 이 클래스에서만 한다.
 */
@Component
public class ContactProtector {

    // AES-GCM AAD로 쓰는 컬럼 이름. 다른 컬럼의 암호문을 옮겨 넣으면 복호화가 실패한다.
    static final String NAME = "name";
    static final String PHONE = "phone";
    static final String EMAIL = "email";

    private final PiiCipher cipher;
    private final PiiHasher hasher;

    public ContactProtector(PiiCipher cipher, PiiHasher hasher) {
        this.cipher = cipher;
        this.hasher = hasher;
    }

    /** 값이 없는 필드는 암호문·해시도 null이다. */
    public ProtectedContact protect(String name, String phone, String email) {
        return new ProtectedContact(cipher.encrypt(name, NAME), cipher.encrypt(phone, PHONE), cipher.encrypt(email, EMAIL),
            hasher.hashPhone(phone), hasher.hashEmail(email));
    }

    /**
     * 원문 연락처. 암호문에서 읽는다. expand 단계에서 아직 백필되지 않은 행(기동 직후 백필이 끝나기 전)만
     * 평문 컬럼으로 대신한다. contract 단계(V5)에서 이 대체를 지운다.
     */
    public LeadContact reveal(Lead lead) {
        ProtectedContact stored = lead.getProtectedContact();
        return new LeadContact(
            stored.nameEnc() == null ? lead.getName() : cipher.decrypt(stored.nameEnc(), NAME),
            stored.phoneEnc() == null ? lead.getPhone() : cipher.decrypt(stored.phoneEnc(), PHONE),
            stored.emailEnc() == null ? lead.getEmail() : cipher.decrypt(stored.emailEnc(), EMAIL));
    }
}
