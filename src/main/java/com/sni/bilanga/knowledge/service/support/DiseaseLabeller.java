package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import com.sni.bilanga.knowledge.repository.DiseaseKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Donne le nom français d'un résultat de diagnostic.
 *
 * <h2>Le manque comblé</h2>
 *
 * <p>Les modèles d'inférence rendent des classes en anglais, telles qu'elles figurent
 * dans les jeux d'entraînement publics : {@code Late_blight},
 * {@code Tomato_Yellow_Leaf_Curl_Virus}, {@code bacterial_blight}. Ces chaînes
 * ressortaient <strong>telles quelles</strong> dans le champ {@code result}, dans le
 * message des alertes et dans la chronologie de la parcelle.
 *
 * <p>Le reste du système est en français, y compris les conseils qui accompagnent ce
 * même diagnostic. L'exploitant lisait donc « Late_blight » suivi d'un traitement
 * rédigé dans sa langue, sans pouvoir relier les deux. C'est le seul endroit du
 * parcours où le domaine cessait d'être francophone.
 *
 * <h2>La traduction existait déjà, personne ne la lisait</h2>
 *
 * <p>{@code disease_knowledge.display_name} porte le nom français depuis la V3
 * (« Mildiou de la tomate », « Bactériose du manioc »). Seul {@code DiseaseRisk}
 * s'en servait. Cette classe en fait le point de passage unique, pour que le nom
 * affiché soit le même partout : diagnostic, alternative, risque, alerte,
 * chronologie.
 *
 * <h2>Ce qui n'est jamais inventé</h2>
 *
 * <p>Un code absent de la base de connaissance n'est <strong>pas traduit</strong> : il
 * est seulement rendu lisible, les tirets bas devenant des espaces. Fabriquer un nom
 * français à partir d'un code inconnu produirait une maladie qui n'existe pas, sous
 * une forme que rien ne distinguerait d'un nom validé. Mieux vaut un intitulé anglais
 * reconnaissable qu'une traduction inventée.
 *
 * <p>Sans état, sans transaction.
 */
@Component
@RequiredArgsConstructor
public class DiseaseLabeller {

    private static final Locale FR = Locale.FRANCE;

    /**
     * Les issues de la chaîne CAPTEUR.
     *
     * <p>Elles ne sont pas des maladies et n'ont donc pas de ligne dans
     * {@code disease_knowledge} : ce sont les catégories que rend le modèle tabulaire.
     * Elles arrivaient pourtant à l'écran sous leur forme technique
     * ({@code STRESS_HYDRIQUE}), majuscules et tirets bas compris.
     */
    private static final Map<String, String> SENSOR_OUTCOMES = Map.of(
            "NORMAL", "Situation normale",
            "STRESS_HYDRIQUE", "Stress hydrique",
            "CARENCES_NUTRITIVES", "Carences nutritives",
            "EXCES_EAU", "Excès d'eau",
            "RISQUE_MALADIE", "Risque de maladie");

    private final DiseaseKnowledgeRepository diseaseKnowledgeRepository;

    /**
     * @param cropName culture du diagnostic, telle que stockée (minuscules) ; peut être nulle
     * @param rawCode  classe rendue par le modèle, normalisée ou non
     * @return le nom français, ou à défaut le code rendu lisible ; {@code null} si le
     *         code est absent
     */
    public String labelFor(String cropName, String rawCode) {
        String code = normalize(rawCode);
        if (code == null) {
            return null;
        }

        String outcome = SENSOR_OUTCOMES.get(code.toUpperCase(FR));
        if (outcome != null) {
            return outcome;
        }

        // Par culture d'abord : le même code peut porter un nom différent selon
        // l'espèce, et « healthy » en est l'exemple exact (« Tomate saine » contre
        // « Manioc sain »).
        if (cropName != null && !cropName.isBlank()) {
            Optional<DiseaseKnowledge> exact = diseaseKnowledgeRepository
                    .findByCropNameAndDiseaseCode(cropName.toLowerCase(FR), code);
            if (exact.isPresent()) {
                return exact.get().getDisplayName();
            }
        }

        // À défaut, toute culture : un diagnostic dont la culture n'a pas pu être
        // résolue vaut mieux nommé qu'anonyme.
        Optional<DiseaseKnowledge> anyCrop =
                diseaseKnowledgeRepository.findFirstByDiseaseCodeIgnoreCase(code);

        return anyCrop.map(DiseaseKnowledge::getDisplayName).orElseGet(() -> readable(code));
    }

    /**
     * Retire le préfixe de culture des classes du modèle vision
     * ({@code Tomato___Late_blight} devient {@code Late_blight}).
     *
     * <p>Repris de {@code KnowledgeService.normalizeDiseaseCode} : l'étiquetage doit
     * fonctionner sur un code brut comme sur un code déjà normalisé, puisqu'il est
     * appelé des deux côtés de la normalisation.
     */
    private String normalize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String trimmed = rawCode.trim();
        int separator = trimmed.lastIndexOf("___");
        return separator >= 0 ? trimmed.substring(separator + 3) : trimmed;
    }

    /** Rendre lisible, sans prétendre traduire. */
    private String readable(String code) {
        String spaced = code.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return code;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
