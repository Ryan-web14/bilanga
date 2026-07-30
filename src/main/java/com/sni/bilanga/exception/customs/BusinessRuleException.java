package com.sni.bilanga.exception.customs;

import com.sni.bilanga.utils.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Violation d'une règle métier signalée volontairement par la couche service :
 * seuils incohérents, doublon, culture inconnue, transition d'état interdite.
 *
 * Remplace l'usage d'{@code IllegalArgumentException} à cette fin. Les deux
 * étaient confondues dans un même gestionnaire, si bien qu'un vrai défaut de
 * programmation ressortait en 400 comme une erreur de saisie de l'appelant.
 * Le message est destiné à l'utilisateur et renvoyé tel quel.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessRuleException extends BaseException {

    public BusinessRuleException(String message) {
        super(message, ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    public BusinessRuleException(String message, String errorCode) {
        super(message, errorCode);
    }

    public BusinessRuleException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }

    /** Transition de cycle de vie refusée (ex. acquitter une alerte déjà résolue). */
    public static BusinessRuleException invalidTransition(String subject, String from, String to) {
        return new BusinessRuleException(
                String.format("%s : passage de %s à %s impossible.", subject, from, to),
                ErrorCode.INVALID_STATE_TRANSITION);
    }
}
