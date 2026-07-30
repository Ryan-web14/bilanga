package com.sni.bilanga.exception.customs;


import com.sni.bilanga.utils.error.ErrorCode;

//TODO: create a custom httpStatus response for this exception
public class BadCredentialException extends BaseException{

    private static final String ERROR_CODE = ErrorCode.BAD_CREDENTIALS;

    public BadCredentialException(String message){
        super(message, ERROR_CODE);
    }

    public BadCredentialException(String message, Throwable cause){
        super(message, ERROR_CODE, cause);
    }

}


