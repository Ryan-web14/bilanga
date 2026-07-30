package com.sni.bilanga.exception.customs;


import com.sni.bilanga.utils.error.ErrorCode;

public class ForbiddenException extends BaseException {

  private static final String ERROR_CODE = ErrorCode.FORBIDDEN;

  public ForbiddenException(String message) {
    super(message, ERROR_CODE);
  }

  public ForbiddenException(String message, Throwable cause) {
    super(message, ERROR_CODE, cause);
  }

}
