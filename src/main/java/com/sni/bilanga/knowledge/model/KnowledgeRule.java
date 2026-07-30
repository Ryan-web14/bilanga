package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "knowledge_rules")
public class KnowledgeRule {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    /**
     * Verrou optimiste : une modification concurrente du même enregistrement
     * échoue au lieu d'écraser silencieusement la précédente (migration V12).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    /** Code du diagnostic capteur : STRESS_HYDRIQUE, SOL_ACIDE, etc. */
    @Column(name = "category", nullable = false)
    private String category;

    /** Culture ciblée, ou '*' pour toutes. */
    @Column(name = "crop_name")
    private String cropName;

    @Column(name = "condition_text", columnDefinition = "text")
    private String conditionText;

    @Column(name = "proposed_action", columnDefinition = "text", nullable = false)
    private String proposedAction;

    @Column(name = "priority")
    private String priority;

    @Column(name = "validated")
    private Boolean validated;

    /**
     * Coût indicatif de l'action, en devise locale <strong>par hectare</strong>
     * (V26, A11).
     *
     * <p><strong>Le défaut corrigé.</strong>
     * {@code recommendations.estimated_cost} existait depuis la V16 et
     * {@code RecommendationResponse} l'exposait déjà au frontend — mais aucune
     * source ne le renseignait, et le champ sortait donc systématiquement à
     * {@code null}. Une capacité annoncée mais inerte coûte plus cher qu'une
     * capacité absente : le client prévoit une colonne qui reste vide, puis en
     * conclut que le backend est cassé.
     *
     * <p><strong>Par hectare</strong>, parce que la règle ignore la parcelle qui
     * la déclenchera : un montant absolu n'aurait de sens que pour une surface
     * donnée. La multiplication revient au calcul économique, qui connaît
     * {@code plantedArea}.
     *
     * <p><strong>{@code null} signifie « non renseigné », jamais « gratuit ».</strong>
     * Zéro est une valeur licite et distincte — un binage manuel ne coûte que du
     * temps — et la contrainte {@code CHECK} de la V26 accepte donc zéro tout en
     * refusant le négatif.
     */
    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private java.math.BigDecimal estimatedCost;

}