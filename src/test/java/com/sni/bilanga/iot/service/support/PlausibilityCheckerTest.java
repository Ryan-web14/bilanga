package com.sni.bilanga.iot.service.support;

import com.sni.bilanga.iot.model.SensorReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaque borne physique, et la distinction qui compte le plus : une mesure
 * <em>absente</em> n'est pas une mesure <em>fausse</em>.
 *
 * <p>Ce contrôle décide si un relevé porte {@code anomalyDetected}. Un faux
 * positif fait apparaître une panne matérielle qui n'existe pas et envoie un
 * technicien pour rien ; un faux négatif laisse une sonde en panne nourrir des
 * diagnostics. Les deux se jouent sur des comparaisons d'une ligne, exactement
 * le genre de code qu'une relecture valide à tort.
 */
@DisplayName("PlausibilityChecker — le physiquement possible, pas l'agronomiquement souhaitable")
class PlausibilityCheckerTest {

    private final PlausibilityChecker checker = new PlausibilityChecker();

    // ============================================================
    // Absence ≠ faute
    // ============================================================

    @Nested
    @DisplayName("Une mesure absente n'est pas une mesure fausse")
    class MissingIsNotWrong {

        /**
         * La distinction fondatrice. Un boîtier ne porte pas forcément toutes les
         * sondes : traiter {@code null} comme hors bornes signalerait une panne à
         * chaque relevé d'un boîtier partiellement équipé, et l'exploitant
         * apprendrait à ignorer le signal.
         */
        @Test
        @DisplayName("un relevé entièrement vide est plausible")
        void emptyReadingIsSound() {
            PlausibilityChecker.Verdict verdict = checker.check(new SensorReading());

            assertThat(verdict.implausible()).isFalse();
            assertThat(verdict.offendingFields()).isEmpty();
            assertThat(verdict.statement())
                    .as("rien à signaler ⇒ rien à afficher")
                    .isNull();
        }

        @Test
        @DisplayName("zéro est une mesure, pas une absence")
        void zeroIsAMeasure() {
            SensorReading reading = new SensorReading();
            reading.setPh(0d);
            reading.setHumiditeSol(0d);
            reading.setAzote(0d);

            assertThat(checker.check(reading).implausible())
                    .as("pH 0 et humidité 0 sont aux bornes, donc dedans")
                    .isFalse();
        }
    }

    // ============================================================
    // Les bornes, une par une
    // ============================================================

    @Nested
    @DisplayName("Bornes encadrées — la valeur limite est ACCEPTÉE")
    class BoundedRanges {

        @ParameterizedTest(name = "pH {0} → plausible ? {1}")
        @CsvSource({"0, true", "7, true", "14, true", "-0.1, false", "14.1, false", "22, false"})
        void ph(double value, boolean sound) {
            assertSound(SensorReading::setPh, value, sound, "pH");
        }

        @ParameterizedTest(name = "humidité du sol {0} % → plausible ? {1}")
        @CsvSource({"0, true", "50, true", "100, true", "-1, false", "101, false", "130, false"})
        void soilHumidity(double value, boolean sound) {
            assertSound(SensorReading::setHumiditeSol, value, sound, "humidité du sol");
        }

        @ParameterizedTest(name = "humidité de l'air {0} % → plausible ? {1}")
        @CsvSource({"0, true", "100, true", "-0.5, false", "100.5, false"})
        void airHumidity(double value, boolean sound) {
            assertSound(SensorReading::setHumiditeAir, value, sound, "humidité de l'air");
        }

        @ParameterizedTest(name = "température de l'air {0} °C → plausible ? {1}")
        @CsvSource({"-20, true", "28, true", "70, true", "-20.1, false", "70.1, false"})
        void airTemperature(double value, boolean sound) {
            assertSound(SensorReading::setTemperature, value, sound, "température de l'air");
        }

        /**
         * <strong>La borne du sol est délibérément plus étroite que celle de
         * l'air.</strong> Le sol tamponne les extrêmes : une sonde enterrée qui
         * affiche 65 °C n'a pas relevé une canicule, elle est hors service. C'est
         * une décision agronomique, et la seule façon de garantir qu'elle survive
         * à une relecture distraite est de la figer par un test.
         */
        @ParameterizedTest(name = "température du sol {0} °C → plausible ? {1}")
        @CsvSource({"-10, true", "24, true", "60, true", "-10.1, false", "60.1, false", "65, false"})
        void soilTemperature(double value, boolean sound) {
            assertSound(SensorReading::setTemperatureSol, value, sound, "température du sol");
        }

        @Test
        @DisplayName("65 °C passe pour l'air et échoue pour le sol — ce sont deux bornes distinctes")
        void soilBoundIsStricterThanAir() {
            SensorReading air = new SensorReading();
            air.setTemperature(65d);
            assertThat(checker.check(air).implausible()).isFalse();

            SensorReading soil = new SensorReading();
            soil.setTemperatureSol(65d);
            assertThat(checker.check(soil).implausible()).isTrue();
        }

        /**
         * Une pluviométrie négative n'existe pas ; 500 mm entre deux relevés
         * dépasse ce qu'un épisode tropical peut produire, même violent.
         */
        @ParameterizedTest(name = "pluviométrie {0} mm → plausible ? {1}")
        @CsvSource({"0, true", "18, true", "500, true", "-0.1, false", "500.1, false"})
        void rainfall(double value, boolean sound) {
            assertSound(SensorReading::setPluviometrie, value, sound, "pluviométrie");
        }
    }

    // ============================================================
    // Bornes basses seules
    // ============================================================

    @Nested
    @DisplayName("Mesures sans maximum — seul le négatif est impossible")
    class NonNegativeOnly {

        /**
         * Aucun plafond sur les nutriments, la luminosité ou la conductivité :
         * leurs valeurs hautes dépendent de l'unité et du capteur, et poser une
         * borne arbitraire signalerait une panne sur un boîtier simplement
         * étalonné autrement. Le négatif, lui, est impossible en toute unité.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"azote", "phosphore", "potassium", "luminosite", "conductivite"})
        @DisplayName("une très grande valeur reste plausible, une valeur négative non")
        void onlyNegativeIsImplausible(String measure) {
            assertThat(checker.check(withMeasure(measure, 999_999d)).implausible())
                    .as("%s : aucun plafond n'est posé", measure)
                    .isFalse();

            assertThat(checker.check(withMeasure(measure, 0d)).implausible()).isFalse();
            assertThat(checker.check(withMeasure(measure, -0.1d)).implausible())
                    .as("%s : une concentration négative n'existe pas", measure)
                    .isTrue();
        }

        private SensorReading withMeasure(String measure, double value) {
            SensorReading reading = new SensorReading();
            switch (measure) {
                case "azote" -> reading.setAzote(value);
                case "phosphore" -> reading.setPhosphore(value);
                case "potassium" -> reading.setPotassium(value);
                case "luminosite" -> reading.setLuminosite(value);
                case "conductivite" -> reading.setConductiviteElectrique(value);
                default -> throw new IllegalArgumentException(measure);
            }
            return reading;
        }
    }

    // ============================================================
    // Le verdict, destiné à être affiché
    // ============================================================

    @Nested
    @DisplayName("Le verdict nomme les sondes en cause")
    class VerdictContent {

        /**
         * Nommer les mesures fautives est le point : un simple booléen laissait
         * l'exploitant deviner quelle sonde changer.
         */
        @Test
        @DisplayName("une seule mesure fautive → formulation au singulier")
        void singleOffenderIsSingular() {
            SensorReading reading = new SensorReading();
            reading.setPh(22d);
            reading.setHumiditeSol(45d);

            PlausibilityChecker.Verdict verdict = checker.check(reading);

            assertThat(verdict.implausible()).isTrue();
            assertThat(verdict.offendingFields()).containsExactly("pH");
            assertThat(verdict.statement())
                    .contains("Mesure hors des valeurs physiquement possibles")
                    .contains("pH")
                    .contains("Sonde vraisemblablement défaillante");
        }

        @Test
        @DisplayName("plusieurs mesures fautives → le boîtier entier est mis en cause")
        void multipleOffendersBlameTheDevice() {
            SensorReading reading = new SensorReading();
            reading.setPh(22d);
            reading.setHumiditeSol(130d);
            reading.setTemperature(120d);

            PlausibilityChecker.Verdict verdict = checker.check(reading);

            assertThat(verdict.offendingFields())
                    .containsExactly("pH", "humidité du sol", "température de l'air");
            assertThat(verdict.statement())
                    .contains("Mesures hors des valeurs")
                    .contains("Boîtier à vérifier");
        }

        /**
         * L'ordre est celui du relevé, pas celui de la découverte : c'est ce qui
         * rend le message reproductible d'un relevé au suivant.
         */
        @Test
        @DisplayName("l'ordre des mesures fautives est stable")
        void orderIsStable() {
            SensorReading reading = new SensorReading();
            reading.setPluviometrie(-5d);
            reading.setPh(99d);
            reading.setAzote(-1d);

            assertThat(checker.check(reading).offendingFields())
                    .containsExactly("pH", "azote", "pluviométrie");
        }

        @Test
        @DisplayName("la liste des fautifs est immuable")
        void offendersAreImmutable() {
            SensorReading reading = new SensorReading();
            reading.setPh(22d);

            var offenders = checker.check(reading).offendingFields();

            assertThat(offenders).isUnmodifiable();
        }
    }

    // ============================================================
    // Ce que ce contrôle NE fait PAS
    // ============================================================

    @Nested
    @DisplayName("Plausibilité matérielle ≠ défavorabilité agronomique")
    class NotAgronomic {

        /**
         * L'invariant à ne jamais mélanger. Un pH de 3,5 est catastrophique pour
         * une tomate, mais parfaitement mesurable : c'est aux moteurs de
         * connaissance de le signaler, pas à ce contrôle. Les confondre ferait
         * marquer « anomalie matérielle » sur un sol réellement acide, et
         * l'exploitant remplacerait une sonde qui fonctionne.
         */
        @Test
        @DisplayName("un sol très acide est plausible, même s'il est mauvais")
        void veryAcidicSoilIsPlausible() {
            SensorReading reading = new SensorReading();
            reading.setPh(3.5);
            reading.setHumiditeSol(4d);      // stress hydrique sévère
            reading.setTemperature(45d);     // canicule
            reading.setAzote(0d);            // carence totale

            assertThat(checker.check(reading).implausible())
                    .as("toutes ces valeurs sont désastreuses ET mesurables")
                    .isFalse();
        }
    }

    // ============================================================
    // Outillage
    // ============================================================
    private void assertSound(BiConsumer<SensorReading, Double> setter, double value,
                             boolean expectedSound, String label) {

        SensorReading reading = new SensorReading();
        setter.accept(reading, value);

        PlausibilityChecker.Verdict verdict = checker.check(reading);

        assertThat(verdict.implausible())
                .as("%s = %s devrait être %s", label, value, expectedSound ? "plausible" : "aberrant")
                .isEqualTo(!expectedSound);

        if (!expectedSound) {
            assertThat(verdict.offendingFields()).containsExactly(label);
        }
    }
}
