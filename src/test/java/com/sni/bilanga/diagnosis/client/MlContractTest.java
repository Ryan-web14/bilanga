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
        // Relevé tel quel sur le service déployé.
        String body = """
                {"category":"STRESS_HYDRIQUE","confidence":0.6852042452444562,
                 "allProbabilities":{"NORMAL":0.14013554447431523,
                                     "STRESS_HYDRIQUE":0.6852042452444562}}
                """;

        SoilPrediction prediction = mapper.readValue(body, SoilPrediction.class);

        assertThat(prediction.getCategory()).isEqualTo("STRESS_HYDRIQUE");
        assertThat(prediction.getConfidence()).isEqualTo(0.6852042452444562);
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
