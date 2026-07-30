package com.sni.bilanga.exception.customs;


import com.sni.bilanga.utils.error.ErrorCode;

public class UnauthorizedException extends BaseException {

  private static final String ERROR_CODE = ErrorCode.UNAUTHORIZED;

  public UnauthorizedException(String message){
    super(message, ERROR_CODE);
  }

  public UnauthorizedException(String message, Throwable cause){
    super(message, ERROR_CODE, cause);
  }

  /**
   * Refus d'authentification avec un code métier précis — par exemple
   * {@code INVALID_DEVICE_KEY} pour un boîtier de terrain, que rien ne
   * distinguait jusqu'ici d'un échec de connexion utilisateur.
   */
  public UnauthorizedException(String message, String errorCode) {
    super(message, errorCode);
  }
}
