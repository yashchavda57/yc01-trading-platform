package com.chavd.yc01.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, String message, T data, LocalDateTime timestamp) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true,null,data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message){
        return new ApiResponse<>(false,message,null, LocalDateTime.now());
    }

}