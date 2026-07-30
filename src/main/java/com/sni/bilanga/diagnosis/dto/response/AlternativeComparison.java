package com.sni.bilanga.diagnosis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pourquoi telle maladie a été retenue plutôt que telle autre.
 *
 * <p><strong>Ce que cela ajoute.</strong> {@code DiagnosisResult} portait déjà
 * les alternatives du classifieur et les risques calculés sur les mesures. Les
 * deux existaient côte à côte sans jamais se rencontrer : le système affichait
 * « mildiou 97 %, alternariose 2 % » sans dire <em>ce qui</em> départage les
 * deux. Or c'est exactement la question qu'un agronome pose, et y répondre est
 * le propre d'un système explicable — par opposition à un classifieur qui rend
 * un verdict.
 *
 * <p>Le rapprochement ne coûte rien de plus : les deux voies avaient déjà
 * produit leurs résultats, il manquait la mise en regard.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AlternativeComparison {

    /** Code de la maladie écartée. */
    private String diseaseCode;
    private String displayName;

    /** Probabilité attribuée par le modèle de vision. */
    private Double modelProbability;

    /**
     * Part des conditions d'apparition réunies pour cette alternative, calculée
     * sur les mesures — indépendamment du modèle. C'est la seconde voie, et
     * c'est elle qui donne son poids à la comparaison.
     */
    private Double riskScore;

    /** Conditions réunies pour la maladie retenue ET pour l'alternative. */
    private List<String> sharedConditions;

    /**
     * Conditions réunies pour la maladie retenue mais pas pour l'alternative.
     * Ce sont elles qui départagent : le reste est commun aux deux.
     */
    private List<String> distinguishingConditions;

    /** Formulation en français, directement affichable. */
    private String statement;
}
