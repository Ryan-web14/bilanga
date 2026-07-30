package com.sni.bilanga.diagnosis.dto.request;

import jakarta.validation.constraints.Future;
import lombok.Data;

import java.time.Instant;

/**
 * Désignation du responsable d'une alerte.
 *
 * <p>Les deux champs sont facultatifs et indépendants : on peut désigner
 * quelqu'un sans fixer de terme, fixer un terme sur une alerte déjà attribuée,
 * ou retirer l'affectation en transmettant {@code userId} nul.
 */
@Data
public class AlertAssignmentRequest {

    /** Responsable désigné ; nul pour retirer l'affectation. */
    private Long userId;

    /**
     * Terme de traitement.
     *
     * Nécessairement à venir : un terme déjà passé au moment où on le fixe
     * naîtrait en retard, ce qui ne décrit aucune intention réelle.
     */
    @Future(message = "L'échéance de traitement doit être à venir")
    private Instant dueAt;
}
