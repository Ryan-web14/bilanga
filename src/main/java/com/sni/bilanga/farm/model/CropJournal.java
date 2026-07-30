package com.sni.bilanga.farm.model;

import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Une entrée du journal de révisions d'un cycle de culture.
 *
 * <h2>Le manque comblé</h2>
 *
 * <p>Un cycle se modifiait sans laisser de trace. Impossible de répondre à « qui a
 * changé la surface plantée, et quand ? » — alors que cette valeur conditionne le
 * rendement à l'hectare, donc la comparaison entre campagnes.
 *
 * <p><strong>Un défaut mis au jour en écrivant ce journal.</strong>
 * {@code CropServiceImpl.update()} écrasait <em>inconditionnellement</em> les champs
 * omis d'un {@code PUT} partiel : la surface plantée disparaissait silencieusement, et
 * le bilan économique devenait incomparable des semaines plus tard. C'est en se
 * demandant ce que le journal allait consigner qu'on l'a vu. Corrigé depuis par
 * {@code CropUpdateMerger} — un champ absent n'est plus touché, et effacer se demande
 * par {@code clearFields}.
 *
 * <p>Les entrées {@code valeur → null} qu'on lit désormais dans {@code changes}
 * décrivent donc des effacements <strong>voulus</strong>. C'est précisément l'opération
 * qui mérite d'être relue : la seule qui détruise de la donnée.
 *
 * <h2>Deux choix de modélisation</h2>
 *
 * <p><strong>Une table dédiée plutôt que {@code audit_log}.</strong> Celui-ci est
 * indexé par module, action et acteur, et sert la supervision transverse. Le journal
 * d'un cycle se lit <em>par cycle</em> et <em>par parcelle</em>, du plus récent au
 * plus ancien — deux accès que {@code audit_log} n'indexe pas.
 *
 * <p>⚠️ <strong>À la différence de {@code SettingsAuditLogs}</strong> (V1), qui est un
 * journal structurellement complet que <em>rien n'a jamais alimenté</em>, celui-ci est
 * écrit dès sa création par {@code CropJournalWriter}. Un second journal mort aurait
 * été la même erreur, en pire — cette fois on la connaissait.
 *
 * <p>Aucun {@code @Version} : une entrée de journal est immuable par nature. Lui
 * donner un verrou optimiste supposerait qu'on la modifie, ce qui contredirait son
 * objet.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "crop_journal")
public class CropJournal {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    /**
     * Identifiant nu plutôt qu'une association {@code @ManyToOne}.
     *
     * <p>Délibéré : le journal ne doit jamais entraîner le chargement du cycle qu'il
     * décrit. Une association ferait qu'afficher trente entrées initialiserait trente
     * fois le même proxy — et surtout, l'écriture d'une entrée n'a besoin que de
     * l'identifiant, pas de l'entité.
     */
    @Column(name = "crop_id", nullable = false)
    private Long cropId;

    /**
     * Dénormalisé depuis le cycle.
     *
     * <p>Permet « le journal de cette parcelle, toutes campagnes confondues » sans
     * jointure, et survit à la lecture d'un cycle archivé.
     */
    @Column(name = "plot_id", nullable = false)
    private Long plotId;

    /**
     * {@code crops.version} <strong>lu avant</strong> la modification.
     *
     * <p>Hibernate incrémente le verrou optimiste au flush : la valeur d'après n'est
     * pas disponible sans {@code flush()} explicite. La colonne est donc nommée pour ce
     * qu'elle est — l'état de départ — et non « version » tout court, qui laisserait
     * croire à la version de l'entrée de journal elle-même.
     */
    @Column(name = "crop_version")
    private Long cropVersion;

    /** Vocabulaire de {@code CropJournalEvent}, miroir du CHECK de la V28. */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    /**
     * Sortie de {@code AuditDiffUtil.diff} : {@code { champ: { before, after } }}.
     *
     * <p>Calculée sur un {@link com.sni.bilanga.farm.service.support.CropSnapshot} —
     * jamais sur l'entité, dont les proxys JPA et les identifiants Snowflake
     * pollueraient le résultat.
     *
     * <p>Jamais {@code null} : une carte vide dit « rien n'a changé », ce qui est une
     * information, là où {@code null} obligerait chaque lecteur à tester.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> changes;

    /** Motif saisi par l'utilisateur, quand l'opération en demande un. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    /**
     * Conservé en clair, et pas seulement par commodité : la FK est
     * {@code ON DELETE SET NULL}, donc un compte supprimé effacerait
     * {@code changedBy}. Un journal qui perd le nom de son auteur perd sa raison
     * d'être.
     */
    @Column(name = "changed_by_email", length = 150)
    private String changedByEmail;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
        if (changes == null) {
            changes = Map.of();
        }
    }
}
