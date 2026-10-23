package com.leadmagnet.crm.common.crypto;

/** 키 교체를 끝낼 수 없다(ADR 0022). 기동을 멈춘다. 메시지에는 행 id만 싣고 원문·해시·키는 싣지 않는다. */
public class PiiKeyRotationException extends IllegalStateException {

    public PiiKeyRotationException(String message) {
        super(message);
    }

    public PiiKeyRotationException(String message, Throwable cause) {
        super(message, cause);
    }
}
