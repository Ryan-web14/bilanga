package com.sni.bilanga.exception.customs;


import com.sni.bilanga.utils.error.ErrorCode;

public class ResourceAlreadyExistException extends BaseException {

  private static final String ERROR_CODE = ErrorCode.RESOURCE_ALREADY_EXISTS;

  public ResourceAlreadyExistException(String message) {
    super(message, ERROR_CODE);
  }
  public ResourceAlreadyExistException(String message, Throwable cause) {
    super(message, ERROR_CODE, cause);
  }
}
