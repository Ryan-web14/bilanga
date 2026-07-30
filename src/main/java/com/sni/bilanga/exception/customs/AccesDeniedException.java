package com.sni.bilanga.exception.customs;


import com.sni.bilanga.utils.error.ErrorCode;

public class AccesDeniedException extends BaseException {

  private static final String ERROR_CODE = ErrorCode.ACCESS_DENIED;

  public AccesDeniedException(String message) {
    super(message, ERROR_CODE);
  }

  private AccesDeniedException(String message, Throwable cause) {
    super(message, ERROR_CODE, cause);
  }

}
