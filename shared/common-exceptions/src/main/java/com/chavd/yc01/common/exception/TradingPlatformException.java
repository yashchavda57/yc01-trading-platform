package com.chavd.yc01.common.exception;

import lombok.Getter;

public class TradingPlatformException extends RuntimeException {

    @Getter
    private final int statusCode;

    public TradingPlatformException(String message, int statusCode) {
        super(message);
        this.statusCode= statusCode;
    }

    public TradingPlatformException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode= statusCode;
    }

}