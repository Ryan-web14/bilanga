package com.sni.bilanga.diagnosis.service.interfaces;


import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.dto.response.PointInTimeView;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisExplanation;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisResult;
import com.sni.bilanga.diagnosis.dto.response.DiagnosticHistoryResponse;
import com.sni.bilanga.enums.DiagnosticSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

public interface DiagnosisService {

    // --- Diagnostics fournis (sans appel aux modèles) ---
    DiagnosisResult processImageDiagnosis(Long plotId, String cropName,
                                          String rawDiseaseClass, Double confidence,
                                          Long readingId, String imageUrl);

    DiagnosisResult processSensorDiagnosis(Long plotId, String cropName,
                                           String category, Double confidence,
                                           Long readingId);

    // --- Diagnostics par les modèles. cropName et readingId sont facultatifs :
    //     ils sont déduits de la parcelle lorsqu'ils ne sont pas fournis.
    DiagnosisResult diagnoseFromImage(Long plotId, String cropName,
                                      MultipartFile image, Long readingId);

    DiagnosisResult diagnoseFromSensorReading(Long plotId, String cropName, Long readingId);

    // --- Historique ---
    DiagnosticHistoryResponse findById(Long id);

    List<DiagnosticHistoryResponse> findByPlot(Long plotId, int limit);

    /** Recherche paginée et filtrable de l'historique. */
    Page<DiagnosticHistoryResponse> search(Long plotId, DiagnosticSource source, String result,
                                           Double minConfidence, Instant from, Instant to,
                                           Pageable pageable);

    /**
     * Justification détaillée d'un diagnostic : quelle règle, quelle mesure,
     * quel seuil pour chaque conseil émis. S'appuie sur les colonnes de
     * traçabilité écrites depuis la migration V9, jusqu'ici jamais exposées.
     */
    DiagnosisExplanation explain(Long id);

    /**
     * Rejoue un diagnostic passé avec les seuils <strong>actuels</strong>, et diffe.
     *
     * <p>Rend la base de connaissance <em>expérimentable</em> : « qu'aurait dit le
     * système, sur ce relevé précis, si ce seuil avait été à 32 % ? ». La question
     * devient vérifiable au lieu d'être théorique — jusqu'ici un agronome ajustait
     * un seuil puis attendait le prochain relevé, sur des conditions différentes de
     * celles qui l'avaient interrogé.
     *
     * <p><strong>N'écrit rien</strong> — ni diagnostic, ni recommandation, ni
     * alerte. Une simulation qui laisserait des traces polluerait l'historique de la
     * parcelle avec des situations qui n'ont pas eu lieu, puis les compterait dans le
     * taux de suivi des conseils — faussant précisément l'indicateur qui sert à
     * réviser les règles.
     */
    DiagnosisReplay replay(Long id);

    /**
     * Ce que le système voyait, avait conclu, et conclurait aujourd'hui — à un
     * instant choisi.
     *
     * <p>Comble le chaînon manquant de l'historique : {@code /plots/{id}/history} rend
     * des intervalles agrégés dont aucun point ne porte d'identifiant de relevé ni de
     * diagnostic. Cliquer sur un creux d'humidité ne menait nulle part.
     *
     * <p>Deux notions de « le diagnostic d'alors » sont exposées séparément, et cette
     * distinction est le cœur de la fonctionnalité : celui <em>issu de ce relevé</em>
     * (rare) et celui <em>en vigueur à cet instant</em> (le cas ordinaire). Le système
     * ne conclut pas à chaque mesure — un intervalle minimal, l'absence de variation,
     * une sonde défaillante ou un service d'inférence muet suffisent à ce qu'un relevé
     * n'ait aucun diagnostic, le relevé étant conservé dans tous les cas.
     *
     * <p><strong>N'écrit rien.</strong>
     *
     * @param at               instant demandé ; le {@code bucket} d'un point
     *                         d'historique convient
     * @param cropName         impose la culture ; {@code null} la déduit du diagnostic
     *                         d'époque, à défaut de la culture en cours
     * @param toleranceMinutes écart maximal accepté entre l'instant et le relevé
     *                         retenu ; {@code null} applique le défaut
     */
    PointInTimeView diagnosisAt(Long plotId, java.time.Instant at, String cropName,
                                Integer toleranceMinutes);
}