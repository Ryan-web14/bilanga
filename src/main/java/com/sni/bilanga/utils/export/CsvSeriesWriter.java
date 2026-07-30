package com.sni.bilanga.utils.export;

import com.sni.bilanga.iot.dto.response.PlotHistoryResponse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Écriture d'une série agrégée au format CSV.
 *
 * <p><strong>Pourquoi le CSV.</strong> Une courbe affichée dans un tableau de
 * bord ne se vérifie pas. En annexe d'un mémoire — ou entre les mains d'un
 * agronome — les données brutes valent la démonstration : elles se recalculent.
 * Un tableur les ouvre sans outil ni compte.
 *
 * <p><strong>Point-virgule et virgule décimale.</strong> Un tableur configuré en
 * français attend le point-virgule comme séparateur de colonnes, parce que la
 * virgule y est le séparateur décimal. Livrer un CSV « standard » à virgules
 * produit un fichier dont toutes les valeurs atterrissent dans la première
 * colonne — techniquement conforme, pratiquement inutilisable.
 */
public final class CsvSeriesWriter {

    private static final char SEPARATOR = ';';
    private static final String NEWLINE = "\r\n";

    private CsvSeriesWriter() {
        throw new IllegalStateException("Utility class");
    }

    public static String write(PlotHistoryResponse history) {
        StringBuilder csv = new StringBuilder();

        // BOM UTF-8 : sans lui, un tableur sous Windows lit le fichier en
        // encodage local et affiche « humiditÃ© » à la place de « humidité ».
        csv.append('﻿');

        List<String> measures = measuresOf(history);
        appendHeader(csv, measures);

        if (history.getPoints() != null) {
            for (PlotHistoryResponse.HistoryPoint point : history.getPoints()) {
                appendRow(csv, point, measures);
            }
        }
        return csv.toString();
    }

    /**
     * Colonnes présentes dans au moins un intervalle.
     *
     * Une mesure jamais relevée n'obtient pas de colonne vide : une colonne
     * intégralement vide se lit comme « la sonde était en panne », alors qu'elle
     * n'existait tout simplement pas.
     */
    private static List<String> measuresOf(PlotHistoryResponse history) {
        Set<String> measures = new LinkedHashSet<>();
        if (history.getPoints() != null) {
            for (PlotHistoryResponse.HistoryPoint point : history.getPoints()) {
                if (point.getMeasures() != null) {
                    measures.addAll(point.getMeasures().keySet());
                }
            }
        }
        return new ArrayList<>(measures);
    }

    private static void appendHeader(StringBuilder csv, List<String> measures) {
        csv.append("intervalle").append(SEPARATOR)
                .append("releves").append(SEPARATOR)
                .append("anomalies");

        // Min, moyenne et max plutôt que la seule moyenne : c'est un pic de
        // température ou un creux d'humidité qui explique un diagnostic, et la
        // moyenne les efface.
        for (String measure : measures) {
            csv.append(SEPARATOR).append(measure).append("_min")
                    .append(SEPARATOR).append(measure).append("_moy")
                    .append(SEPARATOR).append(measure).append("_max");
        }
        csv.append(NEWLINE);
    }

    private static void appendRow(StringBuilder csv, PlotHistoryResponse.HistoryPoint point,
                                  List<String> measures) {

        csv.append(point.getBucket() == null ? "" : point.getBucket().toString()).append(SEPARATOR)
                .append(point.getSampleCount() == null ? 0 : point.getSampleCount()).append(SEPARATOR)
                .append(point.getAnomalyCount() == null ? 0 : point.getAnomalyCount());

        for (String measure : measures) {
            PlotHistoryResponse.MeasureStats stats = point.getMeasures() == null
                    ? null : point.getMeasures().get(measure);

            csv.append(SEPARATOR).append(number(stats == null ? null : stats.min()))
                    .append(SEPARATOR).append(number(stats == null ? null : stats.avg()))
                    .append(SEPARATOR).append(number(stats == null ? null : stats.max()));
        }
        csv.append(NEWLINE);
    }

    /**
     * Nombre à virgule décimale, cellule vide si la mesure est absente.
     *
     * Vide et non zéro : « pas de donnée » et « zéro » ne se confondent pas, et
     * un zéro fabriqué tirerait toutes les moyennes calculées dans le tableur.
     */
    private static String number(Double value) {
        return value == null ? "" : String.format(Locale.FRANCE, "%.2f", value);
    }
}
