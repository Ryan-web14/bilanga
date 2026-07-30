package com.sni.bilanga.diagnosis.dto.request;

import com.sni.bilanga.enums.RecommendationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Suite donnée à un conseil par l'exploitant.
 *
 * Sans ce retour, rien ne permettait de savoir si le moteur conseille juste :
 * les recommandations naissaient toutes en {@code ACTIVE} et y restaient.
 */
@Data
public class RecommendationFeedbackRequest {

    @NotNull(message = "Le statut est obligatoire")
    private RecommendationStatus status;

    /**
     * Motif, surtout utile quand le conseil est écarté : c'est là que se trouve
     * l'information qui permettra d'améliorer la règle.
     */
    @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères")
    private String note;
}
