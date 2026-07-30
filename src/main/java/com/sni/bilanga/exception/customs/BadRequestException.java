package com.sni.bilanga.exception.customs;

import com.sni.bilanga.utils.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends BaseException{
    private final static String ERROR_CODE = ErrorCode.BAD_REQUEST;

    public BadRequestException(String message) {
        super(message, ERROR_CODE);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
}
