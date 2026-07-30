package com.sni.bilanga.diagnosis.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Référentiel d'un modèle d'IA (traçabilité des diagnostics).
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "ai_models")
public class AiModel {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "crop_name")
    private String cropName;

    /** VISION | TABULAR */
    @Column(name = "model_type")
    private String modelType;

    @Column(name = "version")
    private String version;

    @Column(name = "precision_score")
    private Double precisionScore;

    @Column(name = "trained_at")
    private Instant trainedAt;

    @Column(name = "status")
    private String status;

}