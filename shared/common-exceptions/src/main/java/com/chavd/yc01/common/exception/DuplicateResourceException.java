package com.chavd.yc01.common.exception;

public class DuplicateResourceException extends TradingPlatformException {

    public DuplicateResourceException(String message) {
        super(message, 409);
    }

    public DuplicateResourceException(String message,Throwable cause) {
        super(message, 409, cause);
    }
}