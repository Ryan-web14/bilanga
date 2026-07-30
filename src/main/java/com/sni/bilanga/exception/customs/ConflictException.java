package com.sni.bilanga.exception.customs;

import com.sni.bilanga.utils.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends BaseException {
    private static final String ERROR_CODE = ErrorCode.CONFLICT;

    public ConflictException(String message) {
        super(message, ERROR_CODE);
    }

    public ConflictException(String entityName, String conflictReason) {
        super(String.format("Could not process %s: %s", entityName, conflictReason), ERROR_CODE);
    }
}