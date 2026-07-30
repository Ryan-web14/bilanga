package com.sni.bilanga.exception.customs;



import com.sni.bilanga.utils.error.ErrorCode;

import java.util.List;

public class ValidationException extends BaseException {
  private static final String ERROR_CODE = ErrorCode.VALIDATION_FAILED ;
  private final List<String> errors;

  public ValidationException(String message, List<String> errors) {
    super(message, ERROR_CODE);
    this.errors = errors;
  }

  public List<String> getErrors() {
    return errors;
  }

}
