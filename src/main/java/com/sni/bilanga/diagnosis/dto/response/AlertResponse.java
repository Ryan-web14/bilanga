package com.sni.bilanga.diagnosis.dto.response;


import com.sni.bilanga.utils.json.CounterSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AlertResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    private Long diagnosticId;

    /**
     * {@code AGRONOMIQUE} ou {@code TECHNIQUE}.
     *
     * Une panne de sonde ne s'adresse pas à la même personne qu'un risque de
     * mildiou : le filtre existe pour que chacun voie sa liste.
     */
    private String category;
    private String categoryLabel;

    private String level;
    private String levelLabel;

    private String message;

    private String status;
    private String statusLabel;

    /** Vrai tant que l'alerte appelle une action. */
    private Boolean open;

    /**
     * Responsable désigné du traitement.
     *
     * Une alerte sans destinataire n'est traitée par personne : chacun suppose
     * que quelqu'un d'autre s'en charge.
     */
    private Long assignedToUserId;
    private String assignedToName;

    /** Terme de traitement : c'est ce qui transforme un conseil en engagement. */
    private Instant dueAt;

    /** Vrai si le terme est dépassé et que l'alerte est toujours ouverte. */
    private Boolean overdue;

    private Instant createdAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;

    /** Dernière fois que la situation a été reconstatée par un diagnostic. */
    private Instant lastSeenAt;

    /** Depuis combien de temps la situation dure, en heures. Compteur : nombre. */
    @JsonSerialize(using = CounterSerializer.class)
    private Long ageHours;

    /**
     * {@code RESOLUE_MANUELLEMENT}, {@code AUTO_SITUATION_NORMALISEE} ou
     * {@code AUTO_SITUATION_REMPLACEE} — pour savoir si quelqu'un est intervenu
     * ou si le problème a cessé de lui-même.
     */
    private String resolutionReason;

    /** Nombre de reconstats sans acquittement ayant conduit à monter le niveau. */
    private Integer escalationCount;
}
