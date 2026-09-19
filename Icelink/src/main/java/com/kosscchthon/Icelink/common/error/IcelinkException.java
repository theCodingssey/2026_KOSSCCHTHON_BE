package com.kosscchthon.Icelink.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 비즈니스 예외. GlobalExceptionHandler 가 RFC 9457 Problem Details 로 변환한다.
 */
public class IcelinkException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public IcelinkException(ErrorCode code) {
        this(code, code.title(), Map.of());
    }

    public IcelinkException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public IcelinkException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }

    /** Problem Details 본문에 추가로 실릴 확장 필드 (예: activeRoomCode, errors) */
    public Map<String, Object> properties() {
        return properties;
    }
}
