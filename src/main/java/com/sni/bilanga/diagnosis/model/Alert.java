package com.sni.bilanga.diagnosis.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Signalement porté à la connaissance de l'exploitant.
 *
 * Une alerte survit à son diagnostic d'origine : elle reste ouverte jusqu'à
 * ce que quelqu'un en prenne acte ou que la situation cesse. C'est ce qui
 * distingue un système qui répond d'un système qui avertit.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "alerts")
public class Alert {

    public static final String STATUS_NEW = "NOUVELLE";
    public static final String STATUS_ACKNOWLEDGED = "ACQUITTEE";
    public static final String STATUS_RESOLVED = "RESOLUE";

    public static final String CATEGORY_AGRONOMIC = "AGRONOMIQUE";
    public static final String CATEGORY_TECHNICAL = "TECHNIQUE";

    public static final String LEVEL_CRITICAL = "CRITIQUE";
    public static final String LEVEL_HIGH = "ELEVEE";
    public static final String LEVEL_MODERATE = "MOYENNE";

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


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_alert_plot"))
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnostic_id", foreignKey = @ForeignKey(name = "fk_alert_diagnostic"))
    private Diagnostic diagnostic;

    /**
     * Nature de l'alerte : {@code AGRONOMIQUE} ou {@code TECHNIQUE}.
     *
     * Une panne de sonde et un risque de mildiou n'appellent ni le même
     * interlocuteur ni le même délai. Les mêler conduit chacun à filtrer les
     * alertes de l'autre — et, à force, à ne plus lire les siennes.
     */
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "status")
    private String status;

    /**
     * Empreinte de la situation ayant déclenché l'alerte. Tant qu'une alerte
     * ouverte porte la même empreinte sur la même parcelle, aucune nouvelle
     * n'est créée.
     */
    @Column(name = "signature")
    private String signature;

    /**
     * Responsable désigné du traitement.
     *
     * Une alerte sans destinataire n'est traitée par personne : chacun suppose
     * que quelqu'un d'autre s'en charge. Le cycle de vie existait déjà (V8,
     * V13) ; il lui manquait à qui l'adresser.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to", foreignKey = @ForeignKey(name = "fk_alert_assignee"))
    private Users assignedTo;

    /** Terme de traitement : « à traiter sous 48 h » transforme un conseil en engagement. */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Dernière fois que la situation a été reconstatée par un diagnostic.
     * Une alerte que plus rien ne reproduit est une alerte périmée.
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /**
     * Distingue une résolution humaine d'une fermeture automatique — sans quoi
     * on ne peut plus dire si quelqu'un est intervenu ou si le problème a cessé
     * de lui-même.
     */
    @Column(name = "resolution_reason", length = 40)
    private String resolutionReason;

    /**
     * Nombre de fois où la situation a été reconstatée sans acquittement.
     * Au-delà d'un seuil, l'alerte monte d'un niveau : une alerte ignorée ne
     * doit pas rester indéfiniment au même rang que les autres.
     */
    @Column(name = "escalation_count", nullable = false)
    private Integer escalationCount;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = STATUS_NEW;
        // Les alertes du moteur de diagnostic sont agronomiques ; seule la
        // surveillance du parc en produit de techniques, et elle le dit.
        if (category == null) category = CATEGORY_AGRONOMIC;
        if (lastSeenAt == null) lastSeenAt = createdAt;
        if (escalationCount == null) escalationCount = 0;
    }
}
