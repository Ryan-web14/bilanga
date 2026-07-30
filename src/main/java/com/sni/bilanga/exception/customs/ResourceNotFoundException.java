package com.sni.bilanga.exception.customs;

import com.sni.bilanga.utils.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends BaseException {

    private static final String ERROR_CODE = ErrorCode.RESOURCE_NOT_FOUND;

    public ResourceNotFoundException(String message) {
        super(message, ERROR_CODE);
    }

    public ResourceNotFoundException(String message, Throwable cause){
        super(message, ERROR_CODE, cause);
    }

    /**
     * Ressource absente avec un code métier précis — par exemple
     * {@code DEVICE_NOT_REGISTERED} ou {@code NO_ACTIVE_CROP}.
     *
     * Le statut reste 404, mais l'appelant peut distinguer « ce boîtier
     * n'existe pas » de « aucune culture n'est déclarée », ce que le code
     * générique {@code RESOURCE_NOT_FOUND} ne permettait pas.
     */
    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue){
        super(String.format("The ressource %s with the field %s with the value %s not found",
                resourceName, fieldName, fieldValue), ERROR_CODE);
    }

}
