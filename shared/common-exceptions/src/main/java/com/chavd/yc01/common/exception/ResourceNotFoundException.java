package com.chavd.yc01.common.exception;

public class ResourceNotFoundException extends TradingPlatformException {

    public ResourceNotFoundException(String message) {
        super(message, 404);
    }
    public ResourceNotFoundException(String message,Throwable cause) {
        super(message, 404, cause);
    }
}