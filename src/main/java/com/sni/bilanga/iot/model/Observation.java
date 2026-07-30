package com.sni.bilanga.iot.model;


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
 * Constat terrain saisi par un agriculteur, en complément des mesures automatiques.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "observations")
public class Observation {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_obs_plot"))
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_obs_user"))
    private Users user;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @PrePersist
    void onCreate() {
        if (observedAt == null) observedAt = Instant.now();
    }
}