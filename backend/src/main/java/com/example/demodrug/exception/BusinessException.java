package com.example.demodrug.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String requestId;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, String requestId) {
        super(message);
        this.code = code;
        this.requestId = requestId;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }
}
