package com.chavd.yc01.common.exception;

public class UnauthorizedException extends TradingPlatformException {

    public UnauthorizedException(String message) {
        super(message, 401);
    }
    public UnauthorizedException(String message,Throwable cause) {
        super(message, 401, cause);
    }
}