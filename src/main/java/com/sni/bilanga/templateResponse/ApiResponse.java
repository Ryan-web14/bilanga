package com.sni.bilanga.templateResponse;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private Boolean success;
    private String message;
    private String errorCode;
    private String errorDescription;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private T data;

    //Classic success response
    public static <T> ApiResponse<T> success(T data){
        return ApiResponse.<T>builder()
                .message("success")
                .success(true)
                .errorCode("0")
                .errorDescription("No error")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data){
        return ApiResponse.<T>builder()
                .message(message)
                .success(true)
                .errorCode("0")
                .errorDescription("No error")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    //Error response
    public static <T> ApiResponse<T> error(String message, String errorCode, String errorDescription){
        return ApiResponse.<T>builder()
                .message(message)
                .success(false)
                .errorCode(errorCode)
                .errorDescription(errorDescription)
                .timestamp(LocalDateTime.now())
                .build();
    }




}
