package com.sni.bilanga.exception.customs;

public abstract class BaseException extends RuntimeException{

    private final String errorCode;

    public BaseException(String message, String errorCode ) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Le code que l'exception porte déjà. Sans cet accesseur, le gestionnaire
     * global devait redéclarer un code par type d'exception, au risque de le
     * désaligner de celui du constructeur.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
