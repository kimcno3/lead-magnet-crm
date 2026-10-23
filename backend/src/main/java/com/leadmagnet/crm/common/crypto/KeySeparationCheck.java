package com.leadmagnet.crm.common.crypto;

import com.leadmagnet.crm.auth.config.AuthProperties;
import com.leadmagnet.crm.form.config.FormTokenProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * JWT_SECRET이 개인정보 암호화 키·HMAC 키·폼 토큰 키와 같으면 기동을 멈춘다(ADR 0015, 0019). 한 비밀이 두 용도에 쓰이면
 * 한쪽이 새는 순간 다른 쪽도 뚫린다. JWT_SECRET은 문자열 그대로(UTF-8 바이트)이거나 base64로 해독해도 같은 값인지 본다.
 * 오류 메시지에는 어떤 값도 싣지 않는다.
 */
@Component
class KeySeparationCheck {

    KeySeparationCheck(AuthProperties auth, PiiCryptoProperties pii, FormTokenProperties formToken) {
        byte[] raw = auth.jwtSecret().getBytes(StandardCharsets.UTF_8);
        byte[] decoded = decodeOrNull(auth.jwtSecret());
        for (byte[] key : new byte[][] {pii.encKeyBytes(), pii.hmacKeyBytes(), formToken.keyBytes()}) {
            if (Arrays.equals(raw, key) || (decoded != null && Arrays.equals(decoded, key))) {
                throw new IllegalArgumentException("JWT_SECRET은 LEAD_ENC_KEY·LEAD_HMAC_KEY·FORM_TOKEN_KEY와 달라야 합니다");
            }
        }
    }

    private static byte[] decodeOrNull(String value) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
