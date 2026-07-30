package com.sni.bilanga.diagnosis.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
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
@Table(name = "diagnostics")
public class Diagnostic {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_diag_plot"))
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id", foreignKey = @ForeignKey(name = "fk_diag_model"))
    private AiModel aiModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_id", foreignKey = @ForeignKey(name = "fk_diag_reading"))
    private SensorReading reading;

    /** IMAGE | CAPTEUR */
    @Column(name = "source", nullable = false)
    private String source;

    /** Code du diagnostic : ex. Late_blight ou STRESS_HYDRIQUE. */
    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "crop_name")
    private String cropName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "diagnosed_at", nullable = false)
    private Instant diagnosedAt;

    @PrePersist
    void onCreate() {
        if (diagnosedAt == null) diagnosedAt = Instant.now();
    }
}