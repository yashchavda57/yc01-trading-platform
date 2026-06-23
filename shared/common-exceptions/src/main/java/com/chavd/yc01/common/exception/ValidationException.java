package com.chavd.yc01.common.exception;

public class ValidationException extends TradingPlatformException {

    public ValidationException(String message) {
        super(message, 400);
    }
    public ValidationException(String message,Throwable cause) {
        super(message, 400, cause);
    }
}