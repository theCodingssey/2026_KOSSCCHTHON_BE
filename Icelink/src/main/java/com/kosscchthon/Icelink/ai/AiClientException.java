package com.kosscchthon.Icelink.ai;

/** 게이트웨이 호출 실패. 사유별로 FAILED 처리와 폴백 판단에 쓴다. */
public class AiClientException extends RuntimeException {

    private final AiFailureReason reason;

    public AiClientException(AiFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiClientException(AiFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AiFailureReason reason() {
        return reason;
    }
}
