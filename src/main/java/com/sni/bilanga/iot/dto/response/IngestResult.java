package com.sni.bilanga.iot.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Accusé de réception destiné au boîtier.
 *
 * Volontairement compact : un microcontrôleur dispose de peu de mémoire pour
 * analyser une réponse, et n'a besoin que de savoir si son relevé a été retenu
 * et ce qu'il a déclenché.
 *
 * <p>C'est la seule réponse de l'API qui ne soit pas enveloppée dans
 * {@code ApiResponse} : le firmware ESP32 analyse ce corps tel quel, et un
 * niveau d'imbrication supplémentaire coûterait cher sur un microcontrôleur.
 * Les <em>erreurs</em>, elles, suivent bien le format commun {@code ApiError}.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IngestResult {

    private Long readingId;
    private Long plotId;
    private String plotName;

    /** Vrai si une valeur est physiquement impossible : sonde probablement défaillante. */
    private Boolean anomalyDetected;

    /** Mesures en cause, pour savoir quelle sonde vérifier. */
    private List<String> anomalousMeasures;

    /**
     * Verdict de cohérence porté sur les sondes du boîtier :
     * {@code SAINE}, {@code SUSPECTE} ou {@code DEFAILLANTE}.
     *
     * <p>À ne pas confondre avec {@link #anomalyDetected}, qui juge la
     * <em>mesure</em> reçue. Celui-ci juge la <em>sonde</em> sur sa série
     * récente : une sonde figée renvoie des valeurs parfaitement plausibles, et
     * c'est bien ce qui rend le diagnostic qui en découle dangereux.
     */
    private String sensorHealth;

    /** Motif du verdict, en clair : il dit quelle sonde changer. */
    private String sensorHealthReason;

    private Boolean diagnosed;

    /** Code du diagnostic obtenu, lorsque celui-ci a pu être conduit. */
    private String diagnosis;

    /**
     * Nom français du diagnostic.
     *
     * <p>{@code diagnosis} porte le code brut, qui est ce dont un boîtier ou un
     * simulateur a besoin pour comparer sans ambiguïté. Cette réponse est pourtant la
     * première que lit un humain qui met la chaîne au point, et le code y restait la
     * seule chaîne anglaise d'une API francophone.
     */
    private String diagnosisLabel;

    /**
     * Motif lisible par une machine lorsque le diagnostic n'a pas été conduit :
     * {@code CONDITIONS_STABLES}, {@code ML_INDISPONIBLE}, {@code CONTEXTE_ABSENT},
     * {@code SONDE_DEFAILLANTE}.
     * Sans lui, le boîtier ne pouvait pas distinguer un renoncement délibéré
     * d'une panne.
     */
    private String skipReason;

    /** Explication en clair, destinée aux journaux et à l'exploitant. */
    private String message;

    private Integer recommendationCount;

    private Instant recordedAt;
}
