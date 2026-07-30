package com.sni.bilanga.knowledge.service.interfaces;


import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.dto.response.IndicatorSet;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.dto.response.TrendFinding;

import java.util.List;

public interface KnowledgeService {

    List<RecommendationItem> recommendForDisease(String rawDiseaseClass, String cropName, SensorReading reading);

    List<RecommendationItem> recommendForSensorDiagnostic(String category, String cropName);

    /** Constats agronomiques déterministes : seuils de culture et indicateurs dérivés. */
    List<RecommendationItem> analyzeAgronomic(String cropName, String growthStage, SensorReading reading);

    /** Indicateurs calculés, exposés pour lecture. */
    IndicatorSet indicators(String cropName, String growthStage, SensorReading reading);

    /** Maladies dont les conditions d'apparition sont réunies, d'après les seules mesures. */
    List<DiseaseRisk> assessRisks(String cropName, SensorReading reading);

    /** Recommandations préventives issues des risques détectés. */
    List<RecommendationItem> recommendForRisks(List<DiseaseRisk> risks);

    /** Risque calculé pour une maladie donnée, pour confronter deux voies de détection. */
    DiseaseRisk riskFor(String cropName, String diseaseCode, SensorReading reading);

    /** Mesures dont l'évolution annonce un franchissement de seuil. */
    List<TrendFinding> assessTrends(String cropName, String growthStage, Long plotId);

    /** Recommandations anticipatives issues des tendances observées. */
    List<RecommendationItem> recommendForTrends(List<TrendFinding> trends);

    /**
     * Conseils issus des prévisions météo — le sixième moteur.
     *
     * <p>Les cinq autres raisonnent sur le passé mesuré, {@code assessTrends}
     * compris, qui extrapole les mesures internes sans rien savoir du ciel.
     * Celui-ci est le seul à regarder devant à partir d'une source externe.
     *
     * <p>Rend une liste vide si la météo est désactivée, si la parcelle n'a pas
     * de coordonnées ou si le fournisseur ne répond pas : le système doit rester
     * utilisable sans météo, comme il l'est sans microservice d'inférence.
     */
    List<RecommendationItem> assessWeather(Plot plot);

    /**
     * Huitième moteur : risque venu des parcelles voisines (V27).
     *
     * <p>Le seul dont l'information ne peut venir d'aucune mesure locale : une
     * sonde parfaite ne dira jamais qu'un mildiou progresse à huit cents mètres.
     *
     * <p>Rend une liste vide si le moteur est désactivé, si la parcelle n'a pas de
     * coordonnées, ou si aucun voisin ne porte de diagnostic anormal récent — même
     * contrat de dégradation que la météo.
     *
     * @param localRisks risques déjà signalés par les conditions <em>locales</em> ;
     *                   le voisinage se tait sur ces maladies plutôt que d'émettre
     *                   un second conseil sur le même problème. Deux conseils pour
     *                   un même problème font douter du système, pas de la maladie.
     */
    List<RecommendationItem> assessNeighbourhood(Plot plot, List<DiseaseRisk> localRisks);

    /** Synthèses conciliant les conseils qui se contrarient à la lecture. */
    List<RecommendationItem> arbitrate(String cropName, List<RecommendationItem> items);

    /**
     * Reformule les conseils que la parcelle ne peut pas mettre en œuvre.
     *
     * <p>Sur une parcelle pluviale, « irriguez » n'est pas un conseil : c'est un
     * aveu que le système ignore de quoi l'exploitant dispose. Le constat de
     * déficit hydrique reste juste — seule l'action proposée change, pour une
     * qui retient l'eau au lieu d'en apporter.
     */
    List<RecommendationItem> adaptToPlot(Plot plot, List<RecommendationItem> items);

    String normalizeDiseaseCode(String rawClass);
}
