package com.sni.bilanga.diagnosis.service.interfaces;

import com.sni.bilanga.diagnosis.dto.request.RecommendationFeedbackRequest;
import com.sni.bilanga.diagnosis.dto.response.RecommendationResponse;
import com.sni.bilanga.diagnosis.dto.response.RecommendationUptake;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationStatus;
import com.sni.bilanga.enums.RecommendationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Consultation et suivi des conseils émis.
 *
 * Les recommandations sont <em>produites</em> par le moteur de diagnostic ;
 * ce service ne les crée pas, il permet de les retrouver et de leur donner suite.
 */
public interface RecommendationService {

    RecommendationResponse findById(Long id);

    Page<RecommendationResponse> search(Long plotId, Long diagnosticId,
                                        RecommendationStatus status,
                                        RecommendationPriority priority,
                                        RecommendationType type,
                                        Instant from, Instant to, Pageable pageable);

    /** Enregistre la suite donnée par l'exploitant : appliqué, ou écarté et pourquoi. */
    RecommendationResponse recordFeedback(Long id, RecommendationFeedbackRequest request);

    /** Taux d'application par moteur d'origine : mesure de pertinence du système expert. */
    List<RecommendationUptake> uptake(Long plotId);
}
