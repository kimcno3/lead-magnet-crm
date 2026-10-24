package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.crypto.PiiKeyRotationException;
import java.util.Objects;
import java.util.function.BiFunction;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.ProtectedContact;
import org.springframework.stereotype.Component;

/**
 * 신청자 연락처를 저장용(암호문·해시)으로 바꾸고 되돌린다 (ADR 0015). 암·복호화는 이 클래스에서만 한다.
 * 키 교체(ADR 0022)의 재암호화·해시 재계산도 여기서 한다.
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

    /** 원문 연락처. 암호문에서만 읽는다. 값이 없는 필드는 null이다. */
    public LeadContact reveal(Lead lead) {
        ProtectedContact stored = lead.getProtectedContact();
        return new LeadContact(
            cipher.decrypt(stored.nameEnc(), NAME),
            cipher.decrypt(stored.phoneEnc(), PHONE),
            cipher.decrypt(stored.emailEnc(), EMAIL));
    }

    /** 원문 이름만. 전화번호·이메일은 풀지 않는다. 값이 없으면 null이다. */
    public String revealName(Lead lead) {
        return cipher.decrypt(lead.getProtectedContact().nameEnc(), NAME);
    }

    /** 현재 암호화 키가 아닌 암호문만 현재 키로 다시 암호화한다. 해시는 그대로다. 풀 수 없으면 PiiCryptoException. */
    public ProtectedContact reencrypt(ProtectedContact stored) {
        return new ProtectedContact(cipher.reencrypt(stored.nameEnc(), NAME), cipher.reencrypt(stored.phoneEnc(), PHONE),
            cipher.reencrypt(stored.emailEnc(), EMAIL), stored.phoneHash(), stored.emailHash());
    }

    /**
     * 중복 판정 해시를 current HMAC 키로 다시 계산한다 (ADR 0022). 암호문에서 원문을 풀어 계산하며, 저장된 해시가
     * current 키 해시면 그대로, previous 키 해시면 current 키 해시로 바꾼다. 어느 쪽과도 맞지 않거나 해시만 있고 암호문이 없으면
     * {@link PiiKeyRotationException}, 암호문을 풀 수 없으면 PiiCryptoException. 메시지에 원문·해시를 싣지 않는다.
     */
    public ProtectedContact rehash(ProtectedContact stored, PiiHasher previous, PiiHasher current) {
        String phone = cipher.decrypt(stored.phoneEnc(), PHONE);
        String email = cipher.decrypt(stored.emailEnc(), EMAIL);
        return new ProtectedContact(stored.nameEnc(), stored.phoneEnc(), stored.emailEnc(),
            rehash("phone", stored.phoneHash(), phone, previous, current, PiiHasher::hashPhone),
            rehash("email", stored.emailHash(), email, previous, current, PiiHasher::hashEmail));
    }

    private static String rehash(String field, String stored, String value, PiiHasher previous, PiiHasher currentKey,
            BiFunction<PiiHasher, String, String> hash) {
        if (stored == null) {
            return null;
        }
        if (value == null) {
            throw new PiiKeyRotationException(field + " 해시는 있는데 암호문이 없습니다");
        }
        String current = hash.apply(currentKey, value);
        if (Objects.equals(stored, current)) {
            return current;
        }
        if (Objects.equals(stored, hash.apply(previous, value))) {
            return current;
        }
        throw new PiiKeyRotationException(field + " 해시가 이전 HMAC 키로도 현재 HMAC 키로도 맞지 않습니다");
    }
}
