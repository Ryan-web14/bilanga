package com.sni.bilanga.farm.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.organization.model.Farm;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "plots")
public class Plot {

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


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_plot_user"))
    private Users user;

    /**
     * Exploitation de rattachement — <strong>facultative</strong>.
     *
     * <p>Nulle, la parcelle appartient à son utilisateur et se comporte
     * exactement comme avant l'introduction de ce niveau. C'est le cas
     * majoritaire, et il le restera.
     *
     * <p>Renseignée, elle <em>ajoute</em> les membres de l'exploitation à ceux
     * qui peuvent la consulter. Elle ne retire jamais l'accès du propriétaire
     * direct : une exploitation mal configurée ne peut enfermer personne dehors.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", foreignKey = @ForeignKey(name = "fk_plot_farm"))
    private Farm farm;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location")
    private String location;

    /**
     * Référence lisible ({@code PARC-2026-000014}), attribuée automatiquement à
     * la création. Sert à la communication orale et au papier, là où un
     * identifiant Snowflake à dix-neuf chiffres ne se dicte pas.
     */
    @Column(name = "plot_code", length = 40)
    private String plotCode;

    /**
     * Coordonnées décimales.
     *
     * {@code location} est une chaîne libre : « Makotipoko » ne permet ni
     * d'interroger un service météo, ni de calculer une proximité entre
     * parcelles. Ces deux champs débloquent l'un et l'autre.
     */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** Une même température ne s'interprète pas à 300 m et à 900 m. */
    @Column(name = "altitude")
    private Double altitude;

    @Column(name = "soil_type")
    private String soilType;

    /**
     * Moyen d'irrigation disponible, vocabulaire de {@code IrrigationType}.
     * Détermine si un conseil d'arrosage est seulement applicable ici.
     */
    @Column(name = "irrigation_type", length = 30)
    private String irrigationType;

    @Column(name = "area")
    private Double area;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}