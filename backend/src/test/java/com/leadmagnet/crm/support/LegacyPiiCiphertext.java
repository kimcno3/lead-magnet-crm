package com.leadmagnet.crm.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * v0.3.0까지 쓴 암호문 형식 1({@code 1 | iv | ciphertext+tag}, AAD {@code 1:{column}})을 만든다 (ADR 0015, 0022).
 * 지금 코드는 형식 1을 쓰지 않으므로 기존 데이터 호환을 확인하는 테스트가 직접 만든다.
 */
public final class LegacyPiiCiphertext {

    private LegacyPiiCiphertext() {
    }

    public static byte[] v1(byte[] key, String plaintext, String column) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(("1:" + column).getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + 12 + sealed.length).put((byte) 1).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
