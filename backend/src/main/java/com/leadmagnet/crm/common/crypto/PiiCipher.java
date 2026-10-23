package com.leadmagnet.crm.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 개인정보 컬럼 암호화 (ADR 0015, 0022). AES-256-GCM, 값마다 무작위 96비트 IV, 128비트 태그.
 * <ul>
 *   <li>형식 2(새로 쓰는 값): {@code 2 | keyId(1B) | iv(12B) | ciphertext+tag}, AAD {@code 2:{keyId}:{column}}.
 *       키 id까지 AAD로 묶으므로 키 id 바이트를 바꿔 다른 키로 풀게 할 수 없다.</li>
 *   <li>형식 1(v0.3.0까지 쓴 값): {@code 1 | iv(12B) | ciphertext+tag}, AAD {@code 1:{column}}. 키 id 1로 읽는다.</li>
 * </ul>
 * 새로 쓰는 값은 항상 현재 키 id의 형식 2다. 복호화는 알려진 키(현재와 이전) 중 암호문의 키 id에 해당하는 키로 한다.
 * AAD로 컬럼 이름("name", "phone", "email")을 묶으므로 한 컬럼의 암호문을 다른 컬럼에 옮겨 넣으면 복호화가 실패한다.
 * 상태 없는 순수 클래스라 스레드에 안전하다(Cipher는 호출마다 새로 만든다). 오류 메시지에 원문·키를 싣지 않는다.
 */
public final class PiiCipher {

    /** 새로 쓰는 값의 형식 버전. */
    public static final byte VERSION = 2;
    static final byte LEGACY_VERSION = 1;
    /** 형식 1 암호문을 읽을 키 id. */
    public static final int LEGACY_KEY_ID = 1;
    public static final int MIN_KEY_ID = 1;
    public static final int MAX_KEY_ID = 255;
    static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final int currentKeyId;
    private final Map<Integer, SecretKey> keys;
    private final SecureRandom random = new SecureRandom();

    /** 키 하나(키 id 1). */
    public PiiCipher(byte[] key) {
        this(LEGACY_KEY_ID, Map.of(LEGACY_KEY_ID, key == null ? new byte[0] : key));
    }

    /**
     * @param currentKeyId 새로 쓸 때 쓰는 키 id(1~255). keys에 있어야 한다
     * @param keys         키 id → 32바이트 키. 현재 키와 이전 키(읽기 전용)
     */
    public PiiCipher(int currentKeyId, Map<Integer, byte[]> keys) {
        Map<Integer, SecretKey> copy = new HashMap<>();
        keys.forEach((id, key) -> {
            requireKeyId(id);
            if (key == null || key.length != KEY_BYTES) {
                throw new IllegalArgumentException("암호화 키는 " + KEY_BYTES + "바이트여야 합니다");
            }
            copy.put(id, new SecretKeySpec(key.clone(), "AES"));
        });
        requireKeyId(currentKeyId);
        if (!copy.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("현재 키 id의 키가 없습니다");
        }
        this.currentKeyId = currentKeyId;
        this.keys = Map.copyOf(copy);
    }

    public int currentKeyId() {
        return currentKeyId;
    }

    /** 암호문을 만든 키 id. 형식 1은 1, 형식을 모르는 값이나 null은 -1. */
    public static int keyIdOf(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return -1;
        }
        if (stored[0] == LEGACY_VERSION) {
            return LEGACY_KEY_ID;
        }
        if (stored[0] == VERSION && stored.length > 1) {
            return stored[1] & 0xFF;
        }
        return -1;
    }

    /** 현재 키로 만든 암호문인가. null은 다시 암호화할 것이 없으므로 true. */
    public boolean isCurrent(byte[] stored) {
        return stored == null || keyIdOf(stored) == currentKeyId;
    }

    /** null은 null로 둔다. column은 AAD로 묶을 컬럼 이름이다. */
    public byte[] encrypt(String plaintext, String column) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BYTES * 8, iv));
            cipher.updateAAD(aad(VERSION, currentKeyId, column));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(2 + IV_BYTES + sealed.length)
                .put(VERSION).put((byte) currentKeyId).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new PiiCryptoException("개인정보를 암호화하지 못했습니다", e);
        }
    }

    /** null은 null로 둔다. 변조됐거나, 모르는 키·다른 컬럼으로 만든 값이면 {@link PiiCryptoException}. */
    public String decrypt(byte[] stored, String column) {
        if (stored == null) {
            return null;
        }
        if (stored.length == 0 || (stored[0] != VERSION && stored[0] != LEGACY_VERSION)) {
            throw new PiiCryptoException("알 수 없는 암호문 버전입니다");
        }
        byte version = stored[0];
        int header = version == VERSION ? 2 : 1;
        if (stored.length < header + IV_BYTES + TAG_BYTES) {
            throw new PiiCryptoException("암호문 길이가 올바르지 않습니다");
        }
        int keyId = keyIdOf(stored);
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new PiiCryptoException("암호문의 키 id에 해당하는 키가 없습니다(LEAD_ENC_PREVIOUS_KEYS 확인)");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, stored, header, IV_BYTES));
            cipher.updateAAD(aad(version, keyId, column));
            byte[] plain = cipher.doFinal(stored, header + IV_BYTES, stored.length - header - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new PiiCryptoException("암호문이 변조되었거나 키·컬럼이 다릅니다");
        } catch (GeneralSecurityException e) {
            throw new PiiCryptoException("개인정보를 복호화하지 못했습니다", e);
        }
    }

    /**
     * 현재 키가 아닌 키로 만든 암호문을 현재 키로 다시 암호화한다. 이미 현재 키이거나 null이면 그대로 돌려준다.
     * 풀 수 없으면 {@link PiiCryptoException}.
     */
    public byte[] reencrypt(byte[] stored, String column) {
        if (isCurrent(stored)) {
            return stored;
        }
        return encrypt(decrypt(stored, column), column);
    }

    private static byte[] aad(byte version, int keyId, String column) {
        if (column == null || column.isEmpty()) {
            throw new IllegalArgumentException("AAD로 쓸 컬럼 이름이 필요합니다");
        }
        String aad = version == LEGACY_VERSION ? version + ":" + column : version + ":" + keyId + ":" + column;
        return aad.getBytes(StandardCharsets.UTF_8);
    }

    private static void requireKeyId(Integer id) {
        if (id == null || id < MIN_KEY_ID || id > MAX_KEY_ID) {
            throw new IllegalArgumentException("키 id는 " + MIN_KEY_ID + "~" + MAX_KEY_ID + "여야 합니다");
        }
    }
}
