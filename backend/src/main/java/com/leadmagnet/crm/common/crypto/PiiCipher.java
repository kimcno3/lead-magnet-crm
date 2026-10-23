package com.leadmagnet.crm.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 개인정보 컬럼 암호화 (ADR 0015). AES-256-GCM, 값마다 무작위 96비트 IV, 128비트 태그.
 * 저장 형식은 {@code version(1B) | iv(12B) | ciphertext+tag}. 버전 바이트는 이후 키 교체 때 어떤 키로 풀지 가리키는 자리다.
 * 상태 없는 순수 클래스라 스레드에 안전하다(Cipher는 호출마다 새로 만든다).
 */
public final class PiiCipher {

    public static final byte VERSION = 1;
    static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("암호화 키는 " + KEY_BYTES + "바이트여야 합니다");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    /** null은 null로 둔다. */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new PiiCryptoException("개인정보를 암호화하지 못했습니다", e);
        }
    }

    /** null은 null로 둔다. 변조됐거나 다른 키로 만든 값이면 {@link PiiCryptoException}. */
    public String decrypt(byte[] stored) {
        if (stored == null) {
            return null;
        }
        if (stored.length < 1 + IV_BYTES + TAG_BITS / 8) {
            throw new PiiCryptoException("암호문 길이가 올바르지 않습니다");
        }
        if (stored[0] != VERSION) {
            throw new PiiCryptoException("알 수 없는 암호문 버전입니다");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
            byte[] plain = cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new PiiCryptoException("암호문이 변조되었거나 키가 다릅니다");
        } catch (GeneralSecurityException e) {
            throw new PiiCryptoException("개인정보를 복호화하지 못했습니다", e);
        }
    }
}
