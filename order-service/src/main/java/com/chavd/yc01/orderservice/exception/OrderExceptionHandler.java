package com.chavd.yc01.orderservice.exception;

import com.chavd.yc01.common.dto.ApiResponse;
import com.chavd.yc01.common.exception.TradingPlatformException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(TradingPlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handleTradingPlatformException(TradingPlatformException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }
}
