package com.sni.bilanga.utils.export;

import com.sni.bilanga.iot.dto.response.PlotHistoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le format que le tableur francophone attend, et la distinction entre une
 * cellule vide et un zéro.
 *
 * <p>Ce sont trois décisions de format qu'aucune signature n'exprime, et dont
 * chacune rend le fichier inutilisable si elle est inversée : le
 * point-virgule, la virgule décimale, et la marque d'ordre des octets.
 */
@DisplayName("CsvSeriesWriter — un CSV qu'un tableur francophone ouvre correctement")
class CsvSeriesWriterTest {

    /** Marque d'ordre des octets UTF-8, en tête de fichier. */
    private static final String BOM = "﻿";

    // ============================================================
    // Le format
    // ============================================================

    @Nested
    @DisplayName("Format")
    class Format {

        /**
         * <strong>Sans la marque d'ordre des octets</strong>, un tableur sous
         * Windows lit le fichier dans l'encodage local et affiche « humiditÃ© » à
         * la place de « humidité » — sur toutes les lignes, y compris les entêtes.
         */
        @Test
        @DisplayName("le fichier commence par la marque d'ordre des octets UTF-8")
        void startsWithBom() {
            assertThat(CsvSeriesWriter.write(history(point(Instant.EPOCH, 1, 0, Map.of()))))
                    .startsWith(BOM);
        }

        /**
         * <strong>Point-virgule et non virgule.</strong> Un tableur configuré en
         * français attend le point-virgule, parce que la virgule y est le
         * séparateur décimal. Livrer un CSV « standard » à virgules produit un
         * fichier dont toutes les valeurs atterrissent dans la première colonne :
         * techniquement conforme, pratiquement inutilisable.
         */
        @Test
        @DisplayName("les colonnes sont séparées par des points-virgules")
        void columnsAreSemicolonSeparated() {
            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 288, 0,
                            Map.of("humidite_sol", stats(38.1, 44.7, 52.0)))));

            assertThat(header(csv))
                    .isEqualTo("intervalle;releves;anomalies;"
                            + "humidite_sol_min;humidite_sol_moy;humidite_sol_max");
        }

        /**
         * La virgule décimale est la conséquence directe du choix ci-dessus : avec
         * un point décimal ET un point-virgule séparateur, un tableur français
         * lirait les nombres comme du texte et refuserait tout calcul dessus.
         */
        @Test
        @DisplayName("les nombres portent une virgule décimale, à deux décimales")
        void numbersUseDecimalComma() {
            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 288, 0,
                            Map.of("humidite_sol", stats(38.1, 44.666, 52.0)))));

            assertThat(dataRow(csv, 0))
                    .contains("38,10")
                    .contains("44,67")
                    .contains("52,00")
                    .doesNotContain("38.10");
        }

        @Test
        @DisplayName("les lignes se terminent par CRLF")
        void linesEndWithCrLf() {
            String csv = CsvSeriesWriter.write(history(point(Instant.EPOCH, 1, 0, Map.of())));

            assertThat(csv).contains("\r\n");
            assertThat(csv.lines().count()).isEqualTo(2);
        }

        /**
         * Min, moyenne et max plutôt que la seule moyenne : c'est un pic de
         * température ou un creux d'humidité qui explique un diagnostic, et la
         * moyenne les efface précisément.
         */
        @Test
        @DisplayName("chaque mesure donne trois colonnes : min, moyenne, max")
        void eachMeasureYieldsThreeColumns() {
            Map<String, PlotHistoryResponse.MeasureStats> measures = new LinkedHashMap<>();
            measures.put("humidite_sol", stats(38.1, 44.7, 52.0));
            measures.put("temperature", stats(21.0, 27.3, 33.8));

            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 288, 0, measures)));

            assertThat(header(csv).split(";"))
                    .containsExactly("intervalle", "releves", "anomalies",
                            "humidite_sol_min", "humidite_sol_moy", "humidite_sol_max",
                            "temperature_min", "temperature_moy", "temperature_max");
        }
    }

    // ============================================================
    // Vide ≠ zéro
    // ============================================================

    @Nested
    @DisplayName("Une cellule vide n'est pas un zéro")
    class EmptyIsNotZero {

        /**
         * <strong>Le cas qui compte le plus.</strong> Un zéro fabriqué tirerait
         * toutes les moyennes recalculées dans le tableur, et rendrait le fichier
         * faux tout en le rendant plus « propre ». « Pas de donnée » et « zéro » ne
         * se confondent pas.
         */
        @Test
        @DisplayName("une mesure absente sur un intervalle donne trois cellules vides")
        void absentMeasureYieldsEmptyCells() {
            Map<String, PlotHistoryResponse.MeasureStats> withValue =
                    Map.of("humidite_sol", stats(38.1, 44.7, 52.0));

            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 288, 0, withValue),
                    // le boîtier n'a rien transmis sur cet intervalle
                    point(Instant.parse("2026-07-02T00:00:00Z"), 0, 0, Map.of())));

            assertThat(dataRow(csv, 1))
                    .as("trois champs vides, et non trois zéros")
                    .endsWith(";;;")
                    .doesNotContain("0,00");
        }

        @Test
        @DisplayName("un min, une moyenne ou un max nul individuellement reste vide")
        void partialStatsYieldEmptyCells() {
            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 5, 0,
                            Map.of("ph", stats(null, 6.4, null)))));

            assertThat(dataRow(csv, 0)).endsWith(";;6,40;");
        }

        /**
         * Une mesure jamais relevée sur l'intervalle entier n'obtient pas de
         * colonne : une colonne intégralement vide se lit comme « la sonde était en
         * panne », alors qu'elle n'existait tout simplement pas.
         */
        @Test
        @DisplayName("une mesure jamais relevée n'obtient aucune colonne")
        void neverMeasuredYieldsNoColumn() {
            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), 5, 0,
                            Map.of("ph", stats(6.1, 6.4, 6.8)))));

            assertThat(header(csv))
                    .contains("ph_min")
                    .doesNotContain("luminosite");
        }

        /**
         * En revanche, les compteurs de relevés et d'anomalies sont bien des
         * nombres : « aucun relevé » et « aucune anomalie » valent réellement zéro,
         * ce n'est pas une absence de donnée.
         */
        @Test
        @DisplayName("mais les compteurs absents valent zéro, car « aucun » est un nombre")
        void countersDefaultToZero() {
            String csv = CsvSeriesWriter.write(history(
                    point(Instant.parse("2026-07-01T00:00:00Z"), null, null, Map.of())));

            assertThat(dataRow(csv, 0)).isEqualTo("2026-07-01T00:00:00Z;0;0");
        }
    }

    // ============================================================
    // Entrées dégénérées
    // ============================================================

    @Nested
    @DisplayName("Entrées dégénérées")
    class Degenerate {

        @Test
        @DisplayName("une série sans point produit l'entête seul")
        void noPointsYieldsHeaderOnly() {
            PlotHistoryResponse empty = new PlotHistoryResponse();
            empty.setPoints(List.of());

            String csv = CsvSeriesWriter.write(empty);

            assertThat(header(csv)).isEqualTo("intervalle;releves;anomalies");
            assertThat(csv.lines().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("des points nuls ne font pas échouer l'écriture")
        void nullPointsAreTolerated() {
            PlotHistoryResponse history = new PlotHistoryResponse();
            history.setPoints(null);

            assertThat(CsvSeriesWriter.write(history)).startsWith(BOM).contains("intervalle");
        }

        @Test
        @DisplayName("un intervalle sans horodatage donne une cellule vide")
        void nullBucketYieldsEmptyCell() {
            String csv = CsvSeriesWriter.write(history(point(null, 3, 1, Map.of())));

            assertThat(dataRow(csv, 0)).isEqualTo(";3;1");
        }

        @Test
        @DisplayName("la classe utilitaire n'est pas instanciable")
        void utilityClassIsNotInstantiable() throws Exception {
            var constructor = CsvSeriesWriter.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            assertThatThrownBy(constructor::newInstance)
                    .cause()
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    /** Entête, marque d'ordre des octets retirée. */
    private static String header(String csv) {
        return csv.replace(BOM, "").lines().findFirst().orElseThrow();
    }

    private static String dataRow(String csv, int index) {
        return csv.replace(BOM, "").lines().skip(1L + index).findFirst().orElseThrow();
    }

    private static PlotHistoryResponse history(PlotHistoryResponse.HistoryPoint... points) {
        PlotHistoryResponse history = new PlotHistoryResponse();
        history.setPoints(List.of(points));
        return history;
    }

    private static PlotHistoryResponse.HistoryPoint point(
            Instant bucket, Integer sampleCount, Integer anomalyCount,
            Map<String, PlotHistoryResponse.MeasureStats> measures) {

        PlotHistoryResponse.HistoryPoint point = new PlotHistoryResponse.HistoryPoint();
        point.setBucket(bucket);
        point.setSampleCount(sampleCount);
        point.setAnomalyCount(anomalyCount);
        point.setMeasures(measures);
        return point;
    }

    private static PlotHistoryResponse.MeasureStats stats(Double min, Double avg, Double max) {
        return new PlotHistoryResponse.MeasureStats(min, avg, max);
    }
}
