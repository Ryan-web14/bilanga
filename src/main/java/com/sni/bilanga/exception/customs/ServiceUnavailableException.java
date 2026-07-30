package com.sni.bilanga.exception.customs;

import com.sni.bilanga.utils.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Dépendance externe momentanément hors service — en pratique le microservice
 * d'inférence.
 *
 * Distincte d'une erreur interne : rien n'est cassé côté backend, et l'appelant
 * a intérêt à réessayer. Un 500 opaque ne lui disait pas cela.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String message) {
        super(message, ErrorCode.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message, String errorCode) {
        super(message, errorCode);
    }

    public ServiceUnavailableException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }

    /** Le service d'inférence ne répond pas : la lecture reste exploitable sans lui. */
    public static ServiceUnavailableException machineLearning(String detail, Throwable cause) {
        return new ServiceUnavailableException(
                "Le service d'analyse est momentanément indisponible. " + detail,
                ErrorCode.ML_SERVICE_UNAVAILABLE, cause);
    }
}
