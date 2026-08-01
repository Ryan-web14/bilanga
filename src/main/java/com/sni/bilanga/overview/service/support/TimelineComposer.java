package com.sni.bilanga.overview.service.support;

import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.AlertRepository;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.AlertStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.enums.TimelineEventType;
import com.sni.bilanga.knowledge.service.support.DiseaseLabeller;
import com.sni.bilanga.harvest.model.Harvest;
import com.sni.bilanga.harvest.repository.HarvestRepository;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.intervention.repository.InterventionRepository;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.CropRepository;
import com.sni.bilanga.farm.service.support.GrowthStageResolver;
import com.sni.bilanga.iot.model.Observation;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.ObservationRepository;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import com.sni.bilanga.overview.dto.response.PlotTimeline;
import com.sni.bilanga.security.admin.user.model.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Assemble en un flux unique tout ce qui est arrivé à une parcelle.
 *
 * <p><strong>Le travail que cela reprend au client.</strong> Reconstituer
 * l'histoire d'une parcelle demandait quatre appels, une fusion et un tri côté
 * client — refaits différemment par chaque client, avec des décisions
 * divergentes sur ce qui mérite d'y figurer. Or c'est la vue qui explique une
 * situation : un diagnostic pris deux jours après un traitement et trois jours
 * après un changement de stade ne se lit pas comme le même diagnostic isolé.
 *
 * <p><strong>Le filtre, et son coût.</strong> Chaque nature d'événement coûte
 * une requête. Le paramètre {@code types} n'est donc pas un confort d'affichage :
 * il évite d'interroger des sources dont on ne fera rien.
 *
 * <p><strong>Le tri se fait en mémoire</strong>, et c'est assumé. Une union SQL
 * sur cinq tables aux colonnes différentes serait illisible et fragile ; sur une
 * fenêtre bornée dans le temps, avec chaque source plafonnée, le volume reste de
 * l'ordre de la centaine de lignes.
 */
@Component
@RequiredArgsConstructor
public class TimelineComposer {

    private static final Locale FR = Locale.FRANCE;

    private static final String SEVERITY_INFO = "INFO";
    private static final String SEVERITY_WARN = "ATTENTION";
    private static final String SEVERITY_CRITICAL = "CRITIQUE";

    /**
     * Plafond par source.
     *
     * <p>Une parcelle très instrumentée peut porter des milliers d'entrées d'une
     * même nature sur un mois. Sans borne, une seule source noierait toutes les
     * autres et la chronologie perdrait son intérêt : montrer le <em>croisement</em>
     * des événements, pas l'exhaustivité de l'un d'eux.
     */
    private static final int PER_SOURCE_LIMIT = 200;

    private final SensorReadingRepository sensorReadingRepository;
    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;
    private final ObservationRepository observationRepository;
    private final CropRepository cropRepository;
    private final InterventionRepository interventionRepository;
    private final HarvestRepository harvestRepository;
    private final GrowthStageResolver growthStageResolver;
    private final DiseaseLabeller diseaseLabeller;

    /**
     * Résultat d'une composition.
     *
     * <p>{@code truncated} existe pour une raison précise : une chronologie
     * plafonnée se lisait exactement comme une chronologie complète. L'appelant
     * voyait deux cents diagnostics et concluait qu'il n'y en avait pas eu
     * davantage — alors que la borne, et non les faits, avait décidé de la
     * dernière ligne. Sur une vue dont l'objet est de <em>raconter ce qui s'est
     * passé</em>, laisser croire à l'exhaustivité est une erreur de fond, pas un
     * détail de présentation.
     *
     * @param truncated au moins une source a atteint {@code perSourceLimit} ;
     *                  la fenêtre demandée contient donc plus d'événements que
     *                  ce qui est rendu
     * @param saturatedTypes les natures concernées — pour dire <em>laquelle</em>
     *                       est incomplète, et non seulement que quelque chose
     *                       l'est
     */
    public record Composition(List<PlotTimeline.TimelineEntry> entries,
                              boolean truncated,
                              int perSourceLimit,
                              List<String> saturatedTypes) {
    }

    /**
     * @param types natures retenues ; vide signifie « toutes »
     */
    public Composition compose(Plot plot, Instant from, Instant to,
                               Set<TimelineEventType> types) {

        List<PlotTimeline.TimelineEntry> entries = new ArrayList<>();
        List<String> saturated = new ArrayList<>();
        Long plotId = plot.getId();

        if (wants(types, TimelineEventType.RELEVE)) {
            noteIfSaturated(saturated, TimelineEventType.RELEVE,
                    addNotableReadings(entries, plotId, from, to));
        }
        if (wants(types, TimelineEventType.DIAGNOSTIC)) {
            noteIfSaturated(saturated, TimelineEventType.DIAGNOSTIC,
                    addDiagnostics(entries, plotId, from, to));
        }
        if (wants(types, TimelineEventType.ALERTE)) {
            noteIfSaturated(saturated, TimelineEventType.ALERTE,
                    addAlerts(entries, plotId, from, to));
        }
        if (wants(types, TimelineEventType.OBSERVATION)) {
            noteIfSaturated(saturated, TimelineEventType.OBSERVATION,
                    addObservations(entries, plotId, from, to));
        }
        if (wants(types, TimelineEventType.STADE)) {
            // Non plafonné : le nombre de stades d'un cycle est borné par la
            // culture elle-même (quatre ou cinq), pas par une requête.
            addStageChanges(entries, plotId, from, to);
        }
        if (wants(types, TimelineEventType.INTERVENTION)) {
            noteIfSaturated(saturated, TimelineEventType.INTERVENTION,
                    addInterventions(entries, plotId, from, to));
        }
        if (wants(types, TimelineEventType.RECOLTE)) {
            // Non plafonné : la requête porte sur une période, et une parcelle ne
            // produit pas deux cents récoltes.
            addHarvests(entries, plotId, from, to);
        }

        // Du plus récent au plus ancien : on ouvre une chronologie pour savoir
        // ce qui vient de se passer, pas pour relire le début.
        entries.sort(Comparator.comparing(PlotTimeline.TimelineEntry::getOccurredAt).reversed());

        return new Composition(entries, !saturated.isEmpty(), PER_SOURCE_LIMIT, saturated);
    }

    private void noteIfSaturated(List<String> saturated, TimelineEventType type, int fetched) {
        if (fetched >= PER_SOURCE_LIMIT) {
            saturated.add(type.name());
        }
    }

    private boolean wants(Set<TimelineEventType> types, TimelineEventType type) {
        return types == null || types.isEmpty() || types.contains(type);
    }

    // ============================================================
    // Relevés — les marquants seulement
    // ============================================================

    /**
     * Seuls les relevés portant une anomalie matérielle entrent.
     *
     * <p>Une parcelle instrumentée produit un relevé toutes les quelques
     * minutes. Les verser tous rendrait la chronologie illisible et noierait
     * précisément ce qu'on y cherche. Un relevé nominal n'est pas un événement :
     * c'est le fonctionnement normal, et il a déjà sa vue — la série agrégée.
     */
    private int addNotableReadings(List<PlotTimeline.TimelineEntry> out, Long plotId,
                                   Instant from, Instant to) {

        List<SensorReading> readings = sensorReadingRepository
                .search(plotId, null, from, to, true, null, PageRequest.of(0, PER_SOURCE_LIMIT))
                .getContent();

        for (SensorReading reading : readings) {
            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(reading.getRecordedAt())
                    .type(TimelineEventType.RELEVE.name())
                    .typeLabel(TimelineEventType.RELEVE.getLabel())
                    .title("Relevé anormal")
                    .detail("Au moins une mesure est hors des valeurs physiquement possibles. "
                            + "Sonde à vérifier.")
                    .severity(SEVERITY_WARN)
                    .refType("SensorReading")
                    .refId(reading.getId())
                    .build());
        }
        return readings.size();
    }

    // ============================================================
    // Diagnostics
    // ============================================================
    private int addDiagnostics(List<PlotTimeline.TimelineEntry> out, Long plotId,
                               Instant from, Instant to) {

        List<Diagnostic> diagnostics = diagnosticRepository
                .search(plotId, null, null, null, from, to, PageRequest.of(0, PER_SOURCE_LIMIT))
                .getContent();

        for (Diagnostic d : diagnostics) {
            boolean normal = d.getResult() != null && "NORMAL".equalsIgnoreCase(d.getResult());

            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(d.getDiagnosedAt())
                    .type(TimelineEventType.DIAGNOSTIC.name())
                    .typeLabel(TimelineEventType.DIAGNOSTIC.getLabel())
                    .title(String.format(FR, "Diagnostic %s : %s",
                            d.getSource() == null ? "" : d.getSource().toLowerCase(FR),
                            labelOf(d)))
                    .detail(confidenceDetail(d))
                    // Un diagnostic normal est une bonne nouvelle : le présenter
                    // au même rang qu'une détection de maladie ferait perdre le
                    // relief que la chronologie doit justement donner.
                    .severity(normal ? SEVERITY_INFO : SEVERITY_WARN)
                    .refType("Diagnostic")
                    .refId(d.getId())
                    .build());
        }
        return diagnostics.size();
    }

    /**
     * Le nom français du constat, ou son code si la base de connaissance ne le
     * nomme pas. Un titre de chronologie est lu par l'exploitant : « Late_blight »
     * y était le seul fragment d'anglais d'un écran entièrement francophone.
     */
    private String labelOf(Diagnostic d) {
        String label = diseaseLabeller.labelFor(d.getCropName(), d.getResult());
        return label == null ? d.getResult() : label;
    }

    private String confidenceDetail(Diagnostic d) {
        String crop = Culture.canonical(d.getCropName());
        if (d.getConfidenceScore() == null) {
            return crop == null ? null : "Culture : " + crop + ".";
        }
        return String.format(FR, "Confiance %.0f %%%s.",
                d.getConfidenceScore() * 100,
                crop == null ? "" : " · culture : " + crop);
    }

    // ============================================================
    // Alertes
    // ============================================================
    private int addAlerts(List<PlotTimeline.TimelineEntry> out, Long plotId,
                          Instant from, Instant to) {

        List<Alert> alerts = alertRepository
                .search(plotId, null, null, null, false, AlertStatus.OPEN_NAMES,
                        from, to, PageRequest.of(0, PER_SOURCE_LIMIT))
                .getContent();

        for (Alert alert : alerts) {
            AlertLevel level = AlertLevel.from(alert.getLevel());

            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(alert.getCreatedAt())
                    .type(TimelineEventType.ALERTE.name())
                    .typeLabel(TimelineEventType.ALERTE.getLabel())
                    .title(String.format("Alerte %s (%s)",
                            level == null ? alert.getLevel() : level.getLabel().toLowerCase(FR),
                            alert.getCategory() == null
                                    ? "agronomique" : alert.getCategory().toLowerCase(FR)))
                    .detail(alert.getMessage())
                    .severity(level == AlertLevel.CRITIQUE ? SEVERITY_CRITICAL : SEVERITY_WARN)
                    .refType("Alert")
                    .refId(alert.getId())
                    .actor(displayNameOf(alert.getAssignedTo()))
                    .build());

            // La résolution est un événement à part entière : sans elle, la
            // chronologie montrerait une parcelle qui accumule des alertes sans
            // qu'aucune ne se referme jamais.
            if (alert.getResolvedAt() != null && withinWindow(alert.getResolvedAt(), from, to)) {
                out.add(PlotTimeline.TimelineEntry.builder()
                        .occurredAt(alert.getResolvedAt())
                        .type(TimelineEventType.ALERTE.name())
                        .typeLabel(TimelineEventType.ALERTE.getLabel())
                        .title("Alerte résolue")
                        .detail(resolutionDetail(alert))
                        .severity(SEVERITY_INFO)
                        .refType("Alert")
                        .refId(alert.getId())
                        .build());
            }
        }
        // Le décompte porte sur les alertes lues, non sur les entrées produites :
        // une alerte résolue en produit deux, ce qui ferait conclure à tort à une
        // saturation de la source.
        return alerts.size();
    }

    private String resolutionDetail(Alert alert) {
        return switch (alert.getResolutionReason() == null ? "" : alert.getResolutionReason()) {
            case "RESOLUE_MANUELLEMENT" -> "Quelqu'un est intervenu et a clos l'alerte.";
            case "AUTO_SITUATION_NORMALISEE" -> "La situation s'est normalisée d'elle-même : "
                    + "l'alerte a été refermée automatiquement, sans intervention.";
            case "AUTO_SITUATION_REMPLACEE" -> "La situation observée a changé : "
                    + "cette alerte a cédé la place à une autre.";
            default -> null;
        };
    }

    // ============================================================
    // Observations
    // ============================================================
    private int addObservations(List<PlotTimeline.TimelineEntry> out, Long plotId,
                                Instant from, Instant to) {

        List<Observation> observations = observationRepository
                .search(plotId, null, from, to, PageRequest.of(0, PER_SOURCE_LIMIT))
                .getContent();

        for (Observation o : observations) {
            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(o.getObservedAt())
                    .type(TimelineEventType.OBSERVATION.name())
                    .typeLabel(TimelineEventType.OBSERVATION.getLabel())
                    .title("Observation terrain")
                    .detail(o.getNote())
                    .severity(SEVERITY_INFO)
                    .refType("Observation")
                    .refId(o.getId())
                    .actor(displayNameOf(o.getUser()))
                    .build());
        }
        return observations.size();
    }

    // ============================================================
    // Interventions
    // ============================================================

    /**
     * Ce que l'exploitant a fait.
     *
     * <p>C'est l'apport décisif de la chronologie : sans les interventions, une
     * humidité qui remonte passe pour une pluie, et un diagnostic qui
     * s'améliore pour une rémission spontanée. Croiser conseils et actions est
     * précisément ce qui permet de relire une situation.
     */
    private int addInterventions(List<PlotTimeline.TimelineEntry> out, Long plotId,
                                 Instant from, Instant to) {

        List<Intervention> interventions = interventionRepository
                .search(plotId, null, null, from, to, PageRequest.of(0, PER_SOURCE_LIMIT))
                .getContent();

        for (Intervention i : interventions) {
            InterventionType type = InterventionType.from(i.getType());

            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(i.getPerformedAt())
                    .type(TimelineEventType.INTERVENTION.name())
                    .typeLabel(TimelineEventType.INTERVENTION.getLabel())
                    .title(type == null ? i.getType() : type.getLabel())
                    .detail(interventionDetail(i))
                    .severity(SEVERITY_INFO)
                    .refType("Intervention")
                    .refId(i.getId())
                    .actor(displayNameOf(i.getPerformedBy()))
                    .build());
        }
        return interventions.size();
    }

    private String interventionDetail(Intervention i) {
        StringBuilder detail = new StringBuilder();

        if (i.getProduct() != null && !i.getProduct().isBlank()) {
            detail.append(i.getProduct());
        }
        if (i.getDose() != null) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append(String.format(FR, "%.2f%s", i.getDose(),
                    i.getUnit() == null || i.getUnit().isBlank() ? "" : " " + i.getUnit()));
        }
        // Le rattachement à un conseil est l'information la plus parlante ici :
        // il montre que l'action répond au système, et non l'inverse.
        if (i.getRecommendation() != null) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append("suite au conseil ").append(i.getRecommendation().getId());
        }
        if (i.getWeatherNote() != null && !i.getWeatherNote().isBlank()) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append(i.getWeatherNote());
        }
        return detail.isEmpty() ? null : detail.toString();
    }

    // ============================================================
    // Récoltes
    // ============================================================

    /**
     * Le terme de la campagne, et le seul événement qui en donne le résultat.
     *
     * Une chronologie qui s'arrêterait aux conseils et aux actions raconterait
     * l'histoire sans sa fin.
     */
    private void addHarvests(List<PlotTimeline.TimelineEntry> out, Long plotId,
                             Instant from, Instant to) {

        LocalDate fromDate = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate toDate = LocalDate.ofInstant(to, ZoneOffset.UTC);

        for (Harvest harvest : harvestRepository.findForPeriod(plotId, null, fromDate, toDate)) {
            out.add(PlotTimeline.TimelineEntry.builder()
                    .occurredAt(atStartOfDay(harvest.getHarvestedAt()))
                    .type(TimelineEventType.RECOLTE.name())
                    .typeLabel(TimelineEventType.RECOLTE.getLabel())
                    .title("Récolte")
                    .detail(harvestDetail(harvest))
                    .severity(SEVERITY_INFO)
                    .refType("Harvest")
                    .refId(harvest.getId())
                    .build());
        }
    }

    private String harvestDetail(Harvest harvest) {
        StringBuilder detail = new StringBuilder();

        if (harvest.getQuantity() != null) {
            detail.append(String.format(FR, "%.1f%s", harvest.getQuantity(),
                    harvest.getUnit() == null || harvest.getUnit().isBlank()
                            ? "" : " " + harvest.getUnit()));
        }
        if (harvest.getQuality() != null) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append("qualité ").append(harvest.getQuality().toLowerCase(FR));
        }
        return detail.isEmpty() ? null : detail.toString();
    }

    // ============================================================
    // Changements de stade
    // ============================================================

    /**
     * Les changements de stade ne sont enregistrés nulle part — le stade est une
     * colonne écrasée, pas un journal. Mais comme il se déduit de la date de
     * plantation, ces dates se <strong>reconstituent</strong> exactement.
     *
     * <p>Seuls les stades déjà atteints sont versés : annoncer ici une floraison
     * à venir mêlerait le constaté au prévu, ce qu'une chronologie ne doit pas
     * faire.
     */
    private void addStageChanges(List<PlotTimeline.TimelineEntry> out, Long plotId,
                                 Instant from, Instant to) {

        Instant now = Instant.now();

        for (Crop crop : cropRepository.findByPlot_Id(plotId)) {
            for (GrowthStageResolver.StageStart start : growthStageResolver.stageTimeline(crop)) {
                Instant moment = atStartOfDay(start.startsOn());

                if (moment.isAfter(now) || !withinWindow(moment, from, to)) {
                    continue;
                }

                out.add(PlotTimeline.TimelineEntry.builder()
                        .occurredAt(moment)
                        .type(TimelineEventType.STADE.name())
                        .typeLabel(TimelineEventType.STADE.getLabel())
                        .title("Passage en " + start.stage().getLabel().toLowerCase(FR))
                        .detail(String.format(FR,
                                "%s : stade déduit de la date de plantation (%s) et de la durée "
                                        + "du cycle. Les seuils agronomiques applicables changent "
                                        + "à partir d'ici.",
                                Culture.canonical(crop.getCropName()), crop.getPlantingDate()))
                        .severity(SEVERITY_INFO)
                        .refType("Crop")
                        .refId(crop.getId())
                        .build());
            }
        }
    }

    // ============================================================
    // Interne
    // ============================================================
    private boolean withinWindow(Instant moment, Instant from, Instant to) {
        return moment != null && !moment.isBefore(from) && !moment.isAfter(to);
    }

    /**
     * Une date sans heure est ramenée au début du jour, en UTC.
     *
     * Le stade change le jour, pas à une heure précise : lui donner minuit le
     * place avant les événements horodatés du même jour, ce qui est l'ordre juste
     * — le stade est le contexte dans lequel ils surviennent.
     */
    private Instant atStartOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private String displayNameOf(Users user) {
        if (user == null) {
            return null;
        }
        String full = String.join(" ",
                        user.getFirstname() == null ? "" : user.getFirstname(),
                        user.getLastname() == null ? "" : user.getLastname())
                .trim();
        return full.isEmpty() ? user.getEmail() : full;
    }
}
