package com.sni.bilanga.exception;

import com.sni.bilanga.farm.dto.request.PlotRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le message rendu quand le corps d'une requête ne peut pas être lu.
 *
 * <h2>Le cas réel qui a motivé ce test</h2>
 *
 * <p>Un appel à {@code POST /plots} portant {@code "userId": "USR-300726-82U3GVS3"} —
 * l'identifiant <em>lisible</em> à la place de l'identifiant numérique — recevait
 * « Request body is missing or malformed. ». Techniquement exact, et sans aucune valeur :
 * ni le champ fautif, ni la valeur reçue, ni le type attendu.
 *
 * <p>La confusion est prévisible, pas étourdie : l'API expose les deux identifiants, les
 * routes d'administration s'adressent par {@code userCode}, et <strong>tous</strong> les
 * identifiants sortent en chaînes — ce qui efface le seul indice visuel qui aurait
 * distingué les deux.
 *
 * <p>Ces tests figent le message pour que la correction ne se perde pas : ils échouent si
 * quelqu'un revient au message générique.
 */
@DisplayName("Message d'erreur sur un corps illisible")
class UnreadableBodyMessageTest {

    // `describeUnreadableBody` ne touche pas au constructeur de réponse : le passer nul
    // évite d'assembler une infrastructure dont ce test n'a que faire.
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(null);
    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("Type incompatible")
    class WrongType {

        /** Le cas rencontré le 2026-07-30 sur l'instance de production. */
        @Test
        @DisplayName("un userCode là où un identifiant numérique est attendu NOMME le champ")
        void userCodeInsteadOfNumericId() {
            String message = describe("""
                    {"name":"Parcelle Nord","userId":"USR-300726-82U3GVS3"}
                    """);

            assertThat(message)
                    .contains("userId")
                    .contains("USR-300726-82U3GVS3")
                    .contains("nombre entier");

            assertThat(message)
                    .as("le message générique ne doit plus apparaître")
                    .doesNotContain("missing or malformed");
        }

        @Test
        @DisplayName("un identifiant numérique en chaîne reste ACCEPTÉ")
        void numericStringIsAccepted() {
            // Les identifiants Snowflake sortent en chaînes de l'API ; les renvoyer sous
            // cette forme est le geste normal d'un client, et doit fonctionner.
            PlotRequest request = mapper.readValue(
                    "{\"name\":\"Parcelle Nord\",\"userId\":\"1934567890123456789\"}",
                    PlotRequest.class);

            assertThat(request.getUserId()).isEqualTo(1934567890123456789L);
        }

        @Test
        @DisplayName("une surface non numérique nomme aussi son champ")
        void nonNumericArea() {
            assertThat(describe("{\"name\":\"P\",\"area\":\"beaucoup\"}"))
                    .contains("area")
                    .contains("beaucoup");
        }

        /** Une valeur fautive de dix kilo-octets n'aiderait personne. */
        @Test
        @DisplayName("une valeur démesurée est tronquée")
        void oversizedValueIsTruncated() {
            String huge = "X".repeat(500);
            String message = describe("{\"name\":\"P\",\"userId\":\"" + huge + "\"}");

            assertThat(message).contains("…").hasSizeLessThan(200);
        }
    }

    @Nested
    @DisplayName("Ce qui ne change pas")
    class Unchanged {

        @Test
        @DisplayName("une énumération inconnue liste toujours les valeurs acceptées")
        void enumStillListsAcceptedValues() {
            assertThat(describe("{\"name\":\"P\",\"soilType\":\"ROCHEUX\"}"))
                    .contains("ARGILEUX")
                    .contains("Valeurs acceptées");
        }

        @Test
        @DisplayName("un JSON tronqué retombe sur le message générique")
        void malformedJsonKeepsGenericMessage() {
            assertThat(describe("{\"name\":\"P\","))
                    .isEqualTo("Request body is missing or malformed.");
        }
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * Provoque l'échec de lecture pour de vrai, puis interroge le gestionnaire.
     *
     * <p>Fabriquer l'exception à la main donnerait un chemin et un type inventés — donc
     * un test qui valide le test, non le comportement.
     */
    private String describe(String json) {
        try {
            mapper.readValue(json, PlotRequest.class);
            throw new AssertionError("La lecture aurait dû échouer : " + json);
        } catch (RuntimeException failure) {
            return invokeDescribe(new HttpMessageNotReadableException(
                    "corps illisible", failure, null));
        }
    }

    private String invokeDescribe(HttpMessageNotReadableException ex) {
        try {
            Method method = GlobalExceptionHandler.class
                    .getDeclaredMethod("describeUnreadableBody", HttpMessageNotReadableException.class);
            method.setAccessible(true);
            return (String) method.invoke(handler, ex);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "describeUnreadableBody est introuvable — signature modifiée ?", e);
        }
    }
}
