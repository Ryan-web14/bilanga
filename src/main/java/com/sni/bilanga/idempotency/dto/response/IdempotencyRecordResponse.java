package com.sni.bilanga.idempotency.dto.response;

import com.sni.bilanga.idempotency.enums.IdempotencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Vue de lecture d'un enregistrement d'idempotence.
 *
 * <p>Le corps de réponse mémorisé n'est <strong>pas</strong> exposé. C'est la
 * réponse complète d'une opération d'administration — création d'utilisateur,
 * réinitialisation de mot de passe — rejouée telle quelle en cas de doublon.
 * L'entité étant renvoyée nue jusqu'ici, une simple consultation du journal
 * d'idempotence divulguait ces contenus. Seul ce qui sert au diagnostic est
 * conservé : la présence d'une réponse et sa taille.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IdempotencyRecordResponse {

    private Long id;
    private String operation;
    private String idempotencyKey;
    private String requestHash;

    private IdempotencyStatus status;

    /** Vrai si une réponse est mémorisée et sera rejouée sur une clé répétée. */
    private Boolean hasStoredResponse;

    /** Taille du corps mémorisé, en caractères. */
    private Integer storedResponseLength;

    /** Message d'erreur d'un échec, conservé car il ne porte pas de données métier. */
    private String errorBody;

    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
