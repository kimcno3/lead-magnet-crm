package com.leadmagnet.crm.common.crypto;

/** 개인정보 복호화 실패(변조, 다른 키, 모르는 형식). 메시지에 원문이나 키를 싣지 않는다. */
public class PiiCryptoException extends IllegalStateException {

    public PiiCryptoException(String message) {
        super(message);
    }

    public PiiCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
