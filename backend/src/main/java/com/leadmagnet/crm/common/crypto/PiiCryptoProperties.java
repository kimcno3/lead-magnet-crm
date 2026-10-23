package com.leadmagnet.crm.common.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 개인정보 암호화 키 (ADR 0015, 0022). 키는 모두 base64로 인코딩한 32바이트이고 서로 달라야 한다. 아니면 기동을 멈춘다.
 * 오류 메시지에는 키 값을 싣지 않는다.
 *
 * @param encKey          현재 AES-256-GCM 키(LEAD_ENC_KEY). 새로 쓰는 값은 이 키로 암호화한다
 * @param hmacKey         중복 판정 HMAC-SHA256 키(LEAD_HMAC_KEY)
 * @param encKeyId        현재 암호화 키의 id(LEAD_ENC_KEY_ID, 1~255, 비우면 1). 암호문 형식 2에 들어간다
 * @param encPreviousKeys 이전 암호화 키(LEAD_ENC_PREVIOUS_KEYS, 선택). {@code id:base64,id:base64}. 읽기에만 쓴다
 * @param hmacPreviousKey 이전 HMAC 키(LEAD_HMAC_PREVIOUS_KEY, 선택). 설정된 채 기동하면 모든 해시를 현재 키로 바꾼다
 * @param batchSize       재암호화·HMAC 교체에서 한 트랜잭션에 처리할 최대 행 수(기본 500)
 */
@ConfigurationProperties(prefix = "app.pii")
public record PiiCryptoProperties(String encKey, String hmacKey, String encKeyId, String encPreviousKeys,
        String hmacPreviousKey, Integer batchSize) implements SecretKeyMaterial {

    private static final String PREVIOUS_KEYS_RULE =
        "LEAD_ENC_PREVIOUS_KEYS는 쉼표로 구분한 id:base64 목록이어야 합니다(id는 1~255, 키는 base64로 인코딩한 32바이트)";

    public PiiCryptoProperties {
        encKeyId = encKeyId == null || encKeyId.isBlank() ? "1" : encKeyId.trim();
        batchSize = batchSize == null ? 500 : batchSize;
        int currentKeyId = parseKeyId(encKeyId);
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.pii.batch-size는 1 이상이어야 합니다");
        }
        decode("LEAD_ENC_KEY", encKey);
        decode("LEAD_HMAC_KEY", hmacKey);
        Map<Integer, byte[]> previous = parsePrevious(encPreviousKeys, currentKeyId);
        if (!isBlank(hmacPreviousKey)) {
            decode("LEAD_HMAC_PREVIOUS_KEY", hmacPreviousKey);
        }
        requireDistinct(allKeys(encKey, hmacKey, previous, hmacPreviousKey));
    }

    public byte[] encKeyBytes() {
        return decode("LEAD_ENC_KEY", encKey);
    }

    public byte[] hmacKeyBytes() {
        return decode("LEAD_HMAC_KEY", hmacKey);
    }

    /** 현재 암호화 키 id(1~255). */
    public int currentKeyId() {
        return parseKeyId(encKeyId);
    }

    /** 키 id → 암호화 키. 현재 키와 이전 키를 모두 담는다. */
    public Map<Integer, byte[]> encKeyRing() {
        Map<Integer, byte[]> ring = new LinkedHashMap<>(parsePrevious(encPreviousKeys, currentKeyId()));
        ring.put(currentKeyId(), encKeyBytes());
        return Collections.unmodifiableMap(ring);
    }

    /** 이전 HMAC 키. 설정하지 않았으면 비어 있다. */
    public Optional<byte[]> hmacPreviousKeyBytes() {
        return isBlank(hmacPreviousKey) ? Optional.empty()
            : Optional.of(decode("LEAD_HMAC_PREVIOUS_KEY", hmacPreviousKey));
    }

    @Override
    public String name() {
        return "LEAD_ENC_KEY·LEAD_HMAC_KEY";
    }

    /** 현재·이전 암호화 키와 현재·이전 HMAC 키 모두. 다른 비밀(JWT_SECRET·FORM_TOKEN_KEY)과 겹치면 안 된다. */
    @Override
    public List<byte[]> candidates() {
        return allKeys(encKey, hmacKey, parsePrevious(encPreviousKeys, currentKeyId()), hmacPreviousKey);
    }

    @Override
    public String toString() {
        return "PiiCryptoProperties[encKey=***, hmacKey=***, encKeyId=" + encKeyId + ", encPreviousKeys=***, "
            + "hmacPreviousKey=***, batchSize=" + batchSize + "]";
    }

    /** 숫자가 아니어도 Spring 변환 오류 대신 같은 안내를 내려고 문자열로 받아 검사한다. */
    private static int parseKeyId(String value) {
        String rule = "LEAD_ENC_KEY_ID는 1~255여야 합니다";
        int id;
        try {
            id = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(rule);
        }
        if (id < PiiCipher.MIN_KEY_ID || id > PiiCipher.MAX_KEY_ID) {
            throw new IllegalArgumentException(rule);
        }
        return id;
    }

    private static List<byte[]> allKeys(String encKey, String hmacKey, Map<Integer, byte[]> previous,
            String hmacPreviousKey) {
        List<byte[]> keys = new ArrayList<>();
        keys.add(decode("LEAD_ENC_KEY", encKey));
        keys.addAll(previous.values());
        keys.add(decode("LEAD_HMAC_KEY", hmacKey));
        if (!isBlank(hmacPreviousKey)) {
            keys.add(decode("LEAD_HMAC_PREVIOUS_KEY", hmacPreviousKey));
        }
        return keys;
    }

    /** 한 키 값이 두 자리(현재·이전, 암호화·HMAC)에 쓰이면 기동을 멈춘다. */
    private static void requireDistinct(List<byte[]> keys) {
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                if (Arrays.equals(keys.get(i), keys.get(j))) {
                    throw new IllegalArgumentException(
                        "LEAD_ENC_KEY·LEAD_HMAC_KEY·LEAD_ENC_PREVIOUS_KEYS·LEAD_HMAC_PREVIOUS_KEY의 키 값은 서로 달라야 합니다");
                }
            }
        }
    }

    private static Map<Integer, byte[]> parsePrevious(String value, int currentKeyId) {
        Map<Integer, byte[]> keys = new LinkedHashMap<>();
        if (isBlank(value)) {
            return keys;
        }
        for (String entry : value.split(",", -1)) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(PREVIOUS_KEYS_RULE);
            }
            int id;
            try {
                id = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(PREVIOUS_KEYS_RULE);
            }
            if (id < PiiCipher.MIN_KEY_ID || id > PiiCipher.MAX_KEY_ID) {
                throw new IllegalArgumentException(PREVIOUS_KEYS_RULE);
            }
            if (id == currentKeyId) {
                throw new IllegalArgumentException(
                    "LEAD_ENC_PREVIOUS_KEYS에 현재 키 id(LEAD_ENC_KEY_ID)와 같은 id가 있습니다. 새 키에는 새 id를 쓰세요");
            }
            if (keys.containsKey(id)) {
                throw new IllegalArgumentException("LEAD_ENC_PREVIOUS_KEYS에 중복된 키 id가 있습니다");
            }
            keys.put(id, decodeOr(parts[1], PREVIOUS_KEYS_RULE));
        }
        return keys;
    }

    private static byte[] decode(String name, String value) {
        String rule = name + "는 base64로 인코딩한 " + PiiCipher.KEY_BYTES + "바이트여야 합니다(openssl rand -base64 32)";
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + "를 설정해야 합니다. " + rule);
        }
        return decodeOr(value, rule);
    }

    private static byte[] decodeOr(String value, String rule) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(rule);
        }
        if (bytes.length != PiiCipher.KEY_BYTES) {
            throw new IllegalArgumentException(rule);
        }
        return bytes;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
