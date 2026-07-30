package com.sni.bilanga.overview.service.implementation;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.response.AlertResponse;
import com.sni.bilanga.diagnosis.dto.response.DiagnosticHistoryResponse;
import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.diagnosis.service.support.DiagnosisMapper;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.PlotRepository;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.IotDeviceRepository;
import com.sni.bilanga.iot.service.interfaces.SensorReadingService;
import com.sni.bilanga.iot.service.support.IotMapper;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import com.sni.bilanga.knowledge.service.support.RiskEngine;
import com.sni.bilanga.overview.dto.response.PlotOverview;
import com.sni.bilanga.overview.dto.response.PlotSummary;
import com.sni.bilanga.overview.dto.response.PlotTimeline;
import com.sni.bilanga.overview.service.interfaces.OverviewService;
import com.sni.bilanga.overview.service.support.TimelineComposer;
import com.sni.bilanga.enums.TimelineEventType;
import com.sni.bilanga.utils.format.TimeRange;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.DeviceStatus;
import com.sni.bilanga.enums.OverallStatus;
import com.sni.bilanga.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.overview.dto.response.FarmOverview;
import com.sni.bilanga.security.access.AccessGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final Locale FR = Locale.FRANCE;

    // Les statuts viennent des énumérations du domaine : ils étaient redéclarés
    // ici en chaînes, indépendamment du reste du code qui les compare.
    public static final String STATUS_NO_DATA = OverallStatus.SANS_DONNEES.name();
    public static final String STATUS_NORMAL = OverallStatus.NORMAL.name();
    public static final String STATUS_WATCH = OverallStatus.VIGILANCE.name();
    public static final String STATUS_ALERT = OverallStatus.ALERTE.name();
    public static final String STATUS_CRITICAL = OverallStatus.CRITIQUE.name();

    private static final String DEVICE_NONE = DeviceStatus.AUCUN.name();
    private static final String DEVICE_ACTIVE = DeviceStatus.ACTIF.name();
    private static final String DEVICE_SILENT = DeviceStatus.SILENCIEUX.name();

    private final PlotService plotService;
    private final PlotRepository plotRepository;
    private final CropService cropService;
    private final IotDeviceRepository iotDeviceRepository;
    private final SensorReadingService sensorReadingService;
    private final DiagnosticRepository diagnosticRepository;
    private final RecommendationRepository recommendationRepository;
    private final AlertService alertService;
    private final KnowledgeService knowledgeService;
    private final DiagnosisMapper diagnosisMapper;
    private final IotMapper iotMapper;
    private final TimelineComposer timelineComposer;
    private final AccessGuard accessGuard;
    private final BilangaProperties.Overview overviewProperties;

    // ============================================================
    // Chronologie
    // ============================================================

    /**
     * Compose l'histoire de la parcelle, puis la pagine.
     *
     * <p>La pagination est appliquée <strong>après</strong> la fusion, et non par
     * source : demander « les vingt derniers événements » ne veut rien dire si
     * chaque source rend ses vingt derniers séparément — on obtiendrait cent
     * lignes dont les plus récentes d'une source ancienne côtoieraient les plus
     * anciennes d'une source active. Chaque source est bornée en amont, la
     * fenêtre temporelle borne l'ensemble.
     */
    @Override
    @Transactional(readOnly = true)
    public PlotTimeline timelineForPlot(Long plotId, Instant from, Instant to,
                                        Set<TimelineEventType> types, Pageable pageable) {

        Plot plot = plotService.require(plotId);

        Instant start = TimeRange.from(from);
        Instant end = TimeRange.to(to);

        TimelineComposer.Composition composition =
                timelineComposer.compose(plot, start, end, types);
        List<PlotTimeline.TimelineEntry> all = composition.entries();

        // Le décompte porte sur la fenêtre entière, pas sur la page : savoir
        // qu'il y a eu douze alertes ce mois-ci est l'information utile, pas
        // qu'il y en a trois sur l'écran courant.
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlotTimeline.TimelineEntry entry : all) {
            counts.merge(entry.getType(), 1, Integer::sum);
        }

        int offset = (int) Math.min(pageable.getOffset(), all.size());
        int limit = Math.min(offset + pageable.getPageSize(), all.size());

        return PlotTimeline.builder()
                .plotId(plot.getId())
                .plotName(plot.getName())
                .plotCode(plot.getPlotCode())
                .from(start)
                .to(end)
                .entryCount(limit - offset)
                .totalEntries(all.size())
                .countsByType(counts)
                .requestedTypes(types == null || types.isEmpty()
                        ? List.of()
                        : types.stream().map(Enum::name).toList())
                // Une chronologie tronquée se lisait comme une chronologie
                // complète : totalEntries paraissait être le total réel alors
                // qu'il ne comptait que ce qui avait franchi le plafond.
                .truncated(composition.truncated())
                .perSourceLimit(composition.perSourceLimit())
                .truncatedTypes(composition.saturatedTypes())
                .entries(all.subList(offset, limit))
                .build();
    }

    // ============================================================
    // Vue détaillée
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public PlotOverview forPlot(Long plotId) {
        Plot plot = plotService.require(plotId);
        Optional<Crop> crop = cropService.findActiveCrop(plotId);
        String cropName = crop.map(Crop::getCropName).orElse(null);
        String growthStage = crop.map(Crop::getGrowthStage).orElse(null);

        List<IotDevice> devices = iotDeviceRepository.findByPlot_Id(plotId);
        Optional<SensorReading> latest = sensorReadingService.findLatest(plotId);
        List<AlertResponse> alerts = alertService.findByPlot(plotId, true);

        PlotOverview.PlotOverviewBuilder view = PlotOverview.builder()
                .plotId(plot.getId())
                .plotName(plot.getName())
                .location(plot.getLocation())
                .soilType(plot.getSoilType())
                .area(plot.getArea())
                .plotStatus(plot.getStatus())
                .deviceCount(devices.size())
                .lowestBatteryLevel(lowestBattery(devices))
                .deviceStatus(deviceStatus(devices, latest.orElse(null)))
                .openAlertCount(alerts.size())
                .alerts(alerts)
                .generatedAt(Instant.now());

        crop.ifPresent(c -> view
                .cropName(Culture.canonical(c.getCropName()))
                .variety(c.getVariety())
                .plantingDate(c.getPlantingDate())
                .growthStage(c.getGrowthStage())
                .daysSincePlanting(daysSince(c.getPlantingDate())));

        List<DiseaseRisk> risks = List.of();
        if (latest.isPresent()) {
            SensorReading reading = latest.get();
            risks = knowledgeService.assessRisks(cropName, reading);

            view.latestReading(iotMapper.toResponse(reading))
                    .readingAgeMinutes((int) minutesSince(reading.getRecordedAt()))
                    .indicators(knowledgeService.indicators(cropName, growthStage, reading))
                    .risks(risks);
        }

        DiagnosticHistoryResponse diagnostic = latestDiagnostic(plotId);
        view.latestDiagnostic(diagnostic);

        String status = overallStatus(latest.isPresent(), alerts, risks, diagnostic);
        return view.overallStatus(status)
                .summary(summarize(plot, cropName, status, alerts, diagnostic, latest.orElse(null)))
                .build();
    }

    // ============================================================
    // Vue d'ensemble
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public Page<PlotSummary> forAllPlots(Pageable pageable) {
        return plotRepository.findAll(pageable).map(plot -> {
            Long plotId = plot.getId();

            Optional<SensorReading> latest = sensorReadingService.findLatest(plotId);
            List<AlertResponse> alerts = alertService.findByPlot(plotId, true);
            String cropName = cropService.findActiveCrop(plotId).map(Crop::getCropName).orElse(null);

            List<DiseaseRisk> risks = latest
                    .map(reading -> knowledgeService.assessRisks(cropName, reading))
                    .orElse(List.of());

            return PlotSummary.builder()
                    .plotId(plotId)
                    .plotName(plot.getName())
                    .cropName(Culture.canonical(cropName))
                    .overallStatus(overallStatus(latest.isPresent(), alerts, risks, latestDiagnostic(plotId)))
                    .openAlertCount(alerts.size())
                    .lastReadingAt(latest.map(SensorReading::getRecordedAt).orElse(null))
                    .deviceStatus(deviceStatus(iotDeviceRepository.findByPlot_Id(plotId), latest.orElse(null)))
                    .build();
        });
    }


    // ============================================================
    // Synthèse de l'exploitation
    // ============================================================

    /** Charge en deçà de laquelle un boîtier appelle une intervention. */
    private static final int LOW_BATTERY_THRESHOLD = 20;

    /** Au-delà, la liste d'attention cesse d'être lisible. */
    private static final int MAX_ATTENTION_ITEMS = 10;

    @Override
    @Transactional(readOnly = true)
    public FarmOverview forFarm(Long userId) {
        Long owner = accessGuard.resolveOwnerFilter(userId);

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (OverallStatus status : OverallStatus.values()) {
            byStatus.put(status.name(), 0);
        }

        Map<String, Integer> alertsByLevel = new LinkedHashMap<>();
        List<FarmOverview.AttentionItem> attention = new ArrayList<>();

        int plotCount = 0;
        int openAlerts = 0;
        int withoutReading = 0;

        for (Object[] row : plotRepository.aggregateFarmState(owner)) {
            plotCount++;

            Long plotId = ((Number) row[0]).longValue();
            String plotName = (String) row[1];
            int openAlertCount = ((Number) row[2]).intValue();
            int criticalCount = ((Number) row[3]).intValue();
            boolean abnormal = Boolean.TRUE.equals(row[4]);
            boolean hasReading = Boolean.TRUE.equals(row[5]);
            Instant lastReadingAt = toInstant(row[6]);

            openAlerts += openAlertCount;
            if (!hasReading) {
                withoutReading++;
            }

            OverallStatus status = classify(openAlertCount, criticalCount, abnormal, hasReading);
            byStatus.merge(status.name(), 1, Integer::sum);

            if (criticalCount > 0) {
                alertsByLevel.merge(AlertLevel.CRITIQUE.name(), criticalCount, Integer::sum);
            }
            if (openAlertCount - criticalCount > 0) {
                alertsByLevel.merge(AlertLevel.ELEVEE.name(), openAlertCount - criticalCount, Integer::sum);
            }

            if (status.needsAttention()) {
                attention.add(FarmOverview.AttentionItem.builder()
                        .plotId(plotId)
                        .plotName(plotName)
                        .overallStatus(status.name())
                        .openAlertCount(openAlertCount)
                        .lastReadingAt(lastReadingAt)
                        .build());
            }
        }

        // Le plus urgent d'abord : c'est l'ordre dans lequel on veut lire.
        attention.sort(Comparator
                .comparingInt((FarmOverview.AttentionItem item) ->
                        OverallStatus.from(item.getOverallStatus()).getRank())
                .reversed()
                .thenComparing(FarmOverview.AttentionItem::getPlotName,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<FarmOverview.AttentionItem> shortlist = attention.size() > MAX_ATTENTION_ITEMS
                ? attention.subList(0, MAX_ATTENTION_ITEMS)
                : attention;

        return FarmOverview.builder()
                .plotCount(plotCount)
                .plotsByStatus(byStatus)
                .plotsNeedingAttention(List.copyOf(shortlist))
                .openAlertCount(openAlerts)
                .openAlertsByLevel(alertsByLevel)
                .deviceCount((int) iotDeviceRepository.countInService())
                .lowBatteryDeviceCount((int) iotDeviceRepository.countLowBattery(LOW_BATTERY_THRESHOLD))
                .plotsWithoutReading(withoutReading)
                .generatedAt(Instant.now())
                .summary(summarizeFarm(plotCount, byStatus, openAlerts, withoutReading))
                .limitation("La vigilance fondée sur un risque élevé n'est pas prise en compte "
                        + "à ce niveau : elle suppose d'exécuter le moteur de risque sur chaque "
                        + "parcelle. Consultez le détail d'une parcelle pour l'obtenir.")
                .build();
    }

    /**
     * Même précédence que la vue détaillée, au risque près : alerte critique,
     * puis alerte ouverte, puis diagnostic anormal, puis absence de donnée.
     */
    private OverallStatus classify(int openAlerts, int criticalAlerts,
                                   boolean abnormalDiagnosis, boolean hasReading) {
        if (criticalAlerts > 0) return OverallStatus.CRITIQUE;
        if (openAlerts > 0) return OverallStatus.ALERTE;
        if (abnormalDiagnosis) return OverallStatus.VIGILANCE;
        if (!hasReading) return OverallStatus.SANS_DONNEES;
        return OverallStatus.NORMAL;
    }

    private String summarizeFarm(int plotCount, Map<String, Integer> byStatus,
                                 int openAlerts, int withoutReading) {
        if (plotCount == 0) {
            return "Aucune parcelle enregistrée.";
        }

        int critical = byStatus.getOrDefault(OverallStatus.CRITIQUE.name(), 0);
        int alert = byStatus.getOrDefault(OverallStatus.ALERTE.name(), 0);
        int watch = byStatus.getOrDefault(OverallStatus.VIGILANCE.name(), 0);

        if (critical + alert + watch == 0) {
            return String.format(FR,
                    "%d parcelle%s suivie%s, aucune situation à traiter.%s",
                    plotCount, plural(plotCount), plural(plotCount),
                    withoutReading == 0 ? "" : String.format(FR,
                            " %d sans aucun relevé.", withoutReading));
        }

        return String.format(FR,
                "%d parcelle%s suivie%s : %d critique%s, %d en alerte, %d sous surveillance. "
                        + "%d alerte%s ouverte%s au total.",
                plotCount, plural(plotCount), plural(plotCount),
                critical, plural(critical), alert, watch,
                openAlerts, plural(openAlerts), plural(openAlerts));
    }

    private String plural(int count) {
        return count > 1 ? "s" : "";
    }

    private Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case java.sql.Timestamp timestamp -> timestamp.toInstant();
            case null, default -> null;
        };
    }

    // ============================================================
    // Synthèse
    // ============================================================

    /**
     * Une alerte ouverte prime sur tout le reste : c'est la seule information
     * qui appelle une action immédiate. À défaut, un risque élevé ou un
     * diagnostic autre que NORMAL appelle la vigilance.
     */
    private String overallStatus(boolean hasReading, List<AlertResponse> alerts,
                                 List<DiseaseRisk> risks, DiagnosticHistoryResponse diagnostic) {
        if (!hasReading && diagnostic == null) {
            return STATUS_NO_DATA;
        }

        if (alerts.stream().anyMatch(a -> AlertLevel.CRITIQUE == AlertLevel.from(a.getLevel()))) {
            return STATUS_CRITICAL;
        }
        if (!alerts.isEmpty()) {
            return STATUS_ALERT;
        }
        if (risks.stream().anyMatch(r -> Severity.ELEVE == Severity.from(r.getLevel()))) {
            return STATUS_WATCH;
        }
        if (diagnostic != null && diagnostic.getResult() != null
                && !STATUS_NORMAL.equals(diagnostic.getResult())) {
            return STATUS_WATCH;
        }
        return STATUS_NORMAL;
    }

    private String summarize(Plot plot, String cropName, String status,
                             List<AlertResponse> alerts, DiagnosticHistoryResponse diagnostic,
                             SensorReading latest) {

        String culture = cropName == null ? "aucune culture déclarée" : cropName;

        // Aiguillage sur l'énumération plutôt que sur la chaîne : le compilateur
        // vérifie alors que chaque statut du domaine est traité.
        return switch (OverallStatus.from(status)) {
            case SANS_DONNEES -> String.format(FR,
                    "Parcelle %s (%s) : aucune mesure enregistrée à ce jour.",
                    plot.getName(), culture);

            case CRITIQUE, ALERTE -> String.format(FR,
                    "Parcelle %s (%s) : %d alerte%s ouverte%s. %s",
                    plot.getName(), culture, alerts.size(),
                    alerts.size() > 1 ? "s" : "", alerts.size() > 1 ? "s" : "",
                    alerts.isEmpty() ? "" : alerts.getFirst().getMessage());

            case VIGILANCE -> String.format(FR,
                    "Parcelle %s (%s) : surveillance recommandée. Dernier diagnostic : %s.",
                    plot.getName(), culture,
                    diagnostic == null ? "aucun" : diagnostic.getResult());

            default -> String.format(FR,
                    "Parcelle %s (%s) : conditions satisfaisantes%s.",
                    plot.getName(), culture,
                    latest == null ? "" : String.format(FR, ", relevé il y a %d minute%s",
                            minutesSince(latest.getRecordedAt()),
                            minutesSince(latest.getRecordedAt()) > 1 ? "s" : ""));
        };
    }

    // ============================================================
    // Interne
    // ============================================================
    private DiagnosticHistoryResponse latestDiagnostic(Long plotId) {
        return diagnosticRepository.findFirstByPlot_IdOrderByDiagnosedAtDesc(plotId)
                .map(this::toHistory)
                .orElse(null);
    }

    private DiagnosticHistoryResponse toHistory(Diagnostic d) {
        return diagnosisMapper.toHistory(d, recommendationRepository.findByDiagnostic_Id(d.getId()));
    }

    private Integer lowestBattery(List<IotDevice> devices) {
        return devices.stream()
                .map(IotDevice::getBatteryLevel)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    /**
     * Un boîtier installé mais muet depuis longtemps est plus préoccupant
     * qu'une absence de boîtier : la parcelle est réputée surveillée alors
     * qu'elle ne l'est plus.
     *
     * <p><strong>Le contact fait foi, pas le relevé.</strong> Cette décision se
     * fondait auparavant sur la date du dernier relevé, ce qui confondait deux
     * pannes distinctes : un boîtier qui ne répond plus, et un boîtier qui
     * répond mais dont les sondes sont débranchées. Le premier est un problème
     * de communication, le second un problème de matériel — et on n'envoie pas
     * la même personne. {@code lastSeenAt}, mis à jour à chaque contact y
     * compris sur la route de liveness, tranche.
     *
     * <p>Le dernier relevé reste le repli pour les boîtiers enregistrés avant
     * l'introduction de cette colonne, qui n'ont pas encore recontacté le
     * serveur.
     */
    private String deviceStatus(List<IotDevice> devices, SensorReading latest) {
        if (devices.isEmpty()) return DEVICE_NONE;

        Instant lastContact = devices.stream()
                .map(IotDevice::getLastSeenAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElseGet(() -> latest == null ? null : latest.getRecordedAt());

        if (lastContact == null) return DEVICE_SILENT;

        return minutesSince(lastContact) <= overviewProperties.getDeviceSilenceMinutes()
                ? DEVICE_ACTIVE
                : DEVICE_SILENT;
    }

    private long minutesSince(Instant moment) {
        return moment == null ? 0 : Duration.between(moment, Instant.now()).toMinutes();
    }

    private Integer daysSince(LocalDate date) {
        return date == null ? null : (int) ChronoUnit.DAYS.between(date, LocalDate.now());
    }
}
