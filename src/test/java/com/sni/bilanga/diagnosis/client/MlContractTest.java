package com.sni.bilanga.diagnosis.client;

import com.sni.bilanga.diagnosis.client.dto.response.SoilPrediction;
import com.sni.bilanga.diagnosis.client.dto.response.VisionPrediction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contrat avec le microservice d'inférence, figé sur des réponses RÉELLES.
 *
 * <h2>Pourquoi ce test existe</h2>
 *
 * <p>Les charges utiles ci-dessous ont été relevées le 2026-07-30 sur le service déployé
 * ({@code bilanga-ml-587151bad5cb.herokuapp.com}), et non écrites de mémoire. C'est le
 * seul moyen de vérifier que les deux côtés se comprennent : le service évolue dans un
 * autre dépôt, dans un autre langage, sans que rien ne relie les deux à la compilation.
 *
 * <p><strong>Le risque précis qu'il couvre.</strong> {@code MlHttpExchange} construit son
 * propre {@code ObjectMapper} — pas celui de Spring, donc pas celui que
 * {@code JacksonConfig} règle. Si la désérialisation échouait sur un champ inconnu, tout
 * ajout côté Python casserait le diagnostic <em>sans qu'aucun test ne s'en aperçoive</em> :
 * l'échec surviendrait au cœur du pipeline, converti en {@code ML_INDISPONIBLE}, et se
 * lirait comme une panne réseau.
 *
 * <p>Et cet ajout a déjà eu lieu : le service renvoie désormais {@code allProbabilities}
 * sur la prédiction de sol, que {@link SoilPrediction} ne déclare pas.
 */
@DisplayName("Contrat du microservice d'inférence")
class MlContractTest {

    /** Le même que celui de {@code MlHttpExchange} : sans configuration. */
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("un champ INCONNU ne fait pas échouer la lecture d'une prédiction de sol")
    void unknownFieldsAreTolerated() {
        // Relevé tel quel sur le service déployé le 2026-07-30, APRÈS correction :
        // trois champs que SoilPrediction ne déclare pas, dont deux ajoutés depuis
        // le premier relevé. C'est précisément ce que ce test protège — le service
        // évolue dans un autre dépôt, et rien ne relie les deux à la compilation.
        String body = """
                {"category":"STRESS_HYDRIQUE","confidence":0.5824236084577877,
                 "allProbabilities":{"NORMAL":0.14013554447431523,
                                     "STRESS_HYDRIQUE":0.6852042452444562},
                 "imputedFeatures":["type_sol"],
                 "outOfRangeFeatures":["luminosite"]}
                """;

        SoilPrediction prediction = mapper.readValue(body, SoilPrediction.class);

        assertThat(prediction.getCategory()).isEqualTo("STRESS_HYDRIQUE");
        assertThat(prediction.getConfidence()).isEqualTo(0.5824236084577877);
    }

    /**
     * La dégradation de confiance, vue depuis le backend.
     *
     * <p>Le service rend une confiance <strong>déjà pénalisée</strong> par le nombre de
     * mesures imputées : {@code allProbabilities} porte la probabilité brute du modèle,
     * {@code confidence} la valeur retenue. Le backend ne lit que la seconde — et c'est
     * elle qui décide de {@code reliable}, donc de la levée d'une alerte.
     *
     * <p>Relevé réel : cinq mesures absentes sur huit ⇒ 0,4515 brut × 0,4 (le plancher)
     * = 0,1806. Sous le seuil de 0,60, {@code ConfidenceEvaluator} marque le diagnostic
     * non fiable et <strong>aucune alerte n'est levée</strong>. C'est le comportement
     * voulu : ne rien conseiller plutôt que conseiller sur des chiffres fabriqués.
     */
    @Test
    @DisplayName("une confiance dégradée par l'imputation passe sous le seuil de fiabilité")
    void degradedConfidenceIsReadAsIs() {
        String body = """
                {"category":"CARENCES_NUTRITIVES","confidence":0.18060399186665455,
                 "allProbabilities":{"CARENCES_NUTRITIVES":0.45150997966663636},
                 "imputedFeatures":["humidite_sol","humidite_air","azote",
                                    "phosphore","luminosite"]}
                """;

        SoilPrediction prediction = mapper.readValue(body, SoilPrediction.class);

        assertThat(prediction.getConfidence())
                .as("la valeur pénalisée, et non la probabilité brute du modèle")
                .isEqualTo(0.18060399186665455)
                .isLessThan(0.60);
    }

    @Test
    @DisplayName("la prédiction d'image se lit intégralement")
    void visionPayloadIsRead() {
        String body = """
                {"crop":"tomate","diseaseClass":"Tomato___Tomato_mosaic_virus",
                 "confidence":0.45685476064682007,
                 "allProbabilities":{"Tomato___Late_blight":0.19488677382469177,
                                     "Tomato___Tomato_mosaic_virus":0.45685476064682007}}
                """;

        VisionPrediction prediction = mapper.readValue(body, VisionPrediction.class);

        assertThat(prediction.getCrop()).isEqualTo("tomate");
        assertThat(prediction.getDiseaseClass()).isEqualTo("Tomato___Tomato_mosaic_virus");
        assertThat(prediction.getConfidence()).isEqualTo(0.45685476064682007);
        assertThat(prediction.getAllProbabilities()).hasSize(2);
    }

    /**
     * Le piège du <em>snake_case</em>.
     *
     * <p>Un service qui rendrait {@code disease_class} produirait un
     * {@code diseaseClass} nul — donc un diagnostic <strong>sans maladie</strong>, sans
     * erreur, sans trace. Ce test fige la conséquence pour que personne n'ait à la
     * découvrir en production.
     */
    @Test
    @DisplayName("snake_case ⇒ diseaseClass NUL, silencieusement")
    void snakeCaseWouldSilentlyLoseTheDiagnosis() {
        String body = """
                {"crop":"tomate","disease_class":"Tomato___Late_blight","confidence":0.97}
                """;

        VisionPrediction prediction = mapper.readValue(body, VisionPrediction.class);

        assertThat(prediction.getDiseaseClass())
                .as("le camelCase est le contrat — un alias Pydantic doit le rétablir")
                .isNull();
        assertThat(prediction.getConfidence()).isEqualTo(0.97);
    }
}
