package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisResult;
import com.sni.bilanga.diagnosis.service.interfaces.DiagnosisService;
import com.sni.bilanga.diagnosis.service.support.DiagnosisThrottle;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.ReadingQuality;
import com.sni.bilanga.exception.customs.BaseException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.iot.dto.request.IngestBatchRequest;
import com.sni.bilanga.iot.dto.response.IngestBatchResult;
import com.sni.bilanga.exception.customs.ServiceUnavailableException;
import com.sni.bilanga.enums.EquipmentStatus;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.PlotRepository;
import com.sni.bilanga.security.access.MachineContext;
import com.sni.bilanga.iot.dto.request.IngestReadingRequest;
import com.sni.bilanga.iot.dto.response.IngestResult;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.IotDeviceRepository;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import com.sni.bilanga.iot.service.interfaces.IngestService;
import com.sni.bilanga.iot.service.support.PlausibilityChecker;
import com.sni.bilanga.iot.service.support.SensorHealthAnalyzer;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.utils.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class IngestServiceImpl implements IngestService {

    private static final String SKIP_STABLE = "CONDITIONS_STABLES";
    private static final String SKIP_ML = "ML_INDISPONIBLE";
    private static final String SKIP_CONTEXT = "CONTEXTE_ABSENT";
    private static final String SKIP_FAULTY_SENSOR = "SONDE_DEFAILLANTE";

    private final IotDeviceRepository iotDeviceRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DiagnosisService diagnosisService;
    private final DiagnosisThrottle diagnosisThrottle;
    private final PlausibilityChecker plausibilityChecker;
    private final SensorHealthAnalyzer sensorHealthAnalyzer;
    private final AlertService alertService;
    private final BilangaProperties.Diagnosis diagnosisConfig;
    private final BilangaProperties.Ingest ingestConfig;
    private final PlotRepository plotRepository;

    /**
     * Référence à soi-même via le conteneur, pour que le rejeu d'un lot traverse
     * le proxy transactionnel. {@code ObjectProvider} résout à l'appel et non à
     * la construction, ce qui évite la dépendance circulaire.
     */
    private final ObjectProvider<IngestService> self;


    /**
     * Crée le boîtier au premier relevé d'un identifiant inconnu.
     *
     * <p><strong>Le mur que cela lève.</strong> Un {@code technicalId} inconnu était
     * refusé en 404. C'est juste en exploitation — un boîtier fantôme fausserait le
     * parc — mais insurmontable en simulation : le firmware s'authentifie par clé
     * partagée, pas par jeton, et ne peut donc pas appeler {@code POST /devices}.
     * Chaque nouveau montage imposait un enregistrement manuel préalable.
     *
     * <p>Le boîtier créé rejoint ensuite le chemin ordinaire — plausibilité, santé de
     * sonde, diagnostic. Rien d'autre ne change.
     *
     * <p><strong>Le 404 subsiste quand il n'y a AUCUNE parcelle</strong>, et c'est
     * volontaire : un relevé doit se rattacher quelque part, et inventer une parcelle
     * serait fabriquer une donnée métier à partir d'un paquet réseau.
     */
    private IotDevice autoRegister(String technicalId) {
        if (!ingestConfig.getAutoRegister().isEnabled()) {
            throw new ResourceNotFoundException(
                    "Boîtier non enregistré : " + technicalId,
                    ErrorCode.DEVICE_NOT_REGISTERED);
        }

        Plot plot = resolveHostPlot(technicalId);

        IotDevice device = iotDeviceRepository.save(IotDevice.builder()
                .plot(plot)
                .technicalId(technicalId)
                .deviceName(ingestConfig.getAutoRegister().getDeviceNamePrefix() + " " + technicalId)
                .status(EquipmentStatus.ACTIVE.name())
                .build());

        log.warn("Boîtier « {} » INCONNU : enregistré automatiquement sur la parcelle "
                        + "« {} » (id {}). Rattachement corrigeable par PUT /devices/{}. "
                        + "Désactivez bilanga.ingest.auto-register.enabled une fois le parc stabilisé.",
                technicalId, plot.getName(), plot.getId(), device.getId());

        return device;
    }

    /**
     * Parcelle d'accueil : celle qui est configurée, sinon la plus récemment créée.
     *
     * <p>Le repli est une commodité assumée, non une déduction : sur une instance de
     * démonstration, la dernière parcelle créée est presque toujours celle de l'essai
     * en cours. Il est journalisé pour que le rattachement ne soit jamais une surprise.
     */
    private Plot resolveHostPlot(String technicalId) {
        Long configured = ingestConfig.getAutoRegister().getPlotId();

        if (configured != null) {
            return plotRepository.findById(configured).orElseThrow(() ->
                    new ResourceNotFoundException(
                            "Boîtier « " + technicalId + " » inconnu, et la parcelle d'accueil "
                                    + "configurée (bilanga.ingest.auto-register.plot-id = "
                                    + configured + ") est introuvable.",
                            ErrorCode.DEVICE_NOT_REGISTERED));
        }

        return plotRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(0, 1,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id")))
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Boîtier « " + technicalId + " » inconnu, et aucune parcelle n'existe "
                                + "pour l'accueillir. Créez une parcelle, ou enregistrez le "
                                + "boîtier par POST /devices.",
                        ErrorCode.DEVICE_NOT_REGISTERED));
    }

    /**
     * Écrit le relevé, et le <strong>valide sur-le-champ</strong>.
     *
     * <p>C'est la traduction technique de l'invariant premier du système : perdre un
     * diagnostic parce qu'un service tiers est muet est acceptable ; perdre une mesure ne
     * l'est pas — elle est irremplaçable, l'instant est passé.
     *
     * <p>{@code REQUIRES_NEW} suspend la transaction d'ingestion le temps d'écrire. Ce
     * qui suit — mise à jour du boîtier, verdict de sonde, diagnostic — ne peut donc plus
     * emporter le relevé en tombant.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SensorReading persistReading(SensorReading reading) {
        return sensorReadingRepository.save(reading);
    }

    @Override
    @Transactional
    public IngestResult ingest(IngestReadingRequest request) {
        IotDevice device = iotDeviceRepository.findByTechnicalId(request.getTechnicalId())
                .orElseGet(() -> autoRegister(request.getTechnicalId()));

        Plot plot = device.getPlot();

        SensorReading reading = build(request, device, plot);
        PlausibilityChecker.Verdict verdict = plausibilityChecker.check(reading);
        reading.setAnomalyDetected(verdict.implausible());

        // Le relevé est commité IMMÉDIATEMENT, dans sa propre transaction — c'est le
        // seul moyen de tenir l'invariant « le relevé n'est jamais perdu ».
        //
        // Auparavant il attendait le commit de la transaction d'ingestion. Toute
        // exception levée plus bas — jusque dans le diagnostic, pourtant rattrapée —
        // marquait cette transaction rollback-only, et la mesure disparaissait avec
        // elle. Le journal disait « Relevé conservé » pendant que la base n'en gardait
        // rien.
        //
        // Passer par le proxy (self) et non par un appel direct : une invocation
        // interne court-circuite l'intercepteur transactionnel, et REQUIRES_NEW n'aurait
        // aucun effet — c'est le piège classique de l'AOP par proxy.
        reading = self.getObject().persistReading(reading);

        touch(device, request);

        // Analyse de cohérence : elle juge la SONDE, quand le contrôle de
        // plausibilité juge la MESURE. Une sonde figée ou qui dérive produit des
        // valeurs parfaitement crédibles — c'est précisément ce qui rend le
        // diagnostic qui en découle dangereux.
        SensorHealthAnalyzer.Verdict health = assessHealth(device);

        IngestResult.IngestResultBuilder result = IngestResult.builder()
                .readingId(reading.getId())
                .plotId(plot.getId())
                .plotName(plot.getName())
                .anomalyDetected(verdict.implausible())
                .anomalousMeasures(verdict.offendingFields())
                .sensorHealth(health.health() == null ? null : health.health().name())
                .sensorHealthReason(health.reason())
                .recordedAt(reading.getRecordedAt());

        if (health.blocksDiagnosis()) {
            // Mieux vaut ne rien conseiller que conseiller à partir d'une mesure
            // dont on sait qu'elle est fausse. Le relevé, lui, reste enregistré :
            // c'est la trace de la panne.
            return result.diagnosed(false)
                    .skipReason(SKIP_FAULTY_SENSOR)
                    .message("Diagnostic suspendu : " + health.reason())
                    .recommendationCount(0)
                    .build();
        }

        return diagnose(plot.getId(), reading, verdict, result);
    }

    /**
     * Le lot n'est délibérément pas transactionnel dans son ensemble : chaque
     * relevé est enregistré pour son propre compte. Un boîtier qui rentre après
     * trois jours hors ligne ne doit pas perdre cent quatre-vingt-dix-neuf
     * mesures valides parce que la deux-centième est corrompue.
     */
    @Override
    public IngestBatchResult ingestBatch(IngestBatchRequest request) {
        List<IngestResult> results = new ArrayList<>();
        List<IngestBatchResult.BatchFailure> failures = new ArrayList<>();
        int diagnosed = 0;

        List<IngestReadingRequest> readings = request.getReadings();
        for (int index = 0; index < readings.size(); index++) {
            IngestReadingRequest reading = readings.get(index);
            try {
                // Passage par le proxy Spring, et non par `this` : un appel
                // direct court-circuiterait l'intercepteur et priverait chaque
                // relevé de sa transaction — un échec en cours de lot laisserait
                // alors des écritures partielles derrière lui.
                IngestResult result = self.getObject().ingest(reading);
                results.add(result);
                if (Boolean.TRUE.equals(result.getDiagnosed())) {
                    diagnosed++;
                }
            } catch (BaseException e) {
                failures.add(failure(index, reading, e.getErrorCode(), e.getMessage()));
            } catch (Exception e) {
                log.error("Relevé {} du lot rejeté", index, e);
                failures.add(failure(index, reading, ErrorCode.INTERNAL_SERVER_ERROR,
                        "Relevé rejeté : " + e.getMessage()));
            }
        }

        return IngestBatchResult.builder()
                .received(readings.size())
                .accepted(results.size())
                .rejected(failures.size())
                .diagnosed(diagnosed)
                .results(results)
                .failures(failures)
                .build();
    }

    private IngestBatchResult.BatchFailure failure(int index, IngestReadingRequest reading,
                                                   String errorCode, String message) {
        return IngestBatchResult.BatchFailure.builder()
                .index(index)
                .technicalId(reading == null ? null : reading.getTechnicalId())
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    // ============================================================
    // Enregistrement
    // ============================================================
    private SensorReading build(IngestReadingRequest r, IotDevice device, Plot plot) {
        return SensorReading.builder()
                .plot(plot)
                .device(device)
                .temperature(r.getTemperature())
                .temperatureSol(r.getTemperatureSol())
                .humiditeSol(r.getHumiditeSol())
                .humiditeAir(r.getHumiditeAir())
                .ph(r.getPh())
                .azote(r.getAzote())
                .phosphore(r.getPhosphore())
                .potassium(r.getPotassium())
                .luminosite(r.getLuminosite())
                .pluviometrie(r.getPluviometrie())
                .conductiviteElectrique(r.getConductiviteElectrique())
                .signalStrength(r.getSignalStrength())
                // Un boîtier qui a tamponné ses relevés pendant une coupure les
                // rejoue avec leur heure réelle ; sans elle, toute la série
                // s'écraserait sur l'instant de reconnexion.
                .recordedAt(r.getRecordedAt())
                .quality(DomainEnums.nameOf(
                        r.getQuality() == null ? ReadingQuality.TERRAIN : r.getQuality()))
                .build();
    }

    /**
     * Enregistre ce que le boîtier vient de dire de lui-même.
     *
     * <p>{@code lastSeenAt} est mis à jour <strong>à chaque contact</strong>,
     * qu'il apporte ou non une mesure exploitable. La vue d'ensemble déduisait
     * jusqu'ici le silence d'un boîtier de l'absence de relevé sur la parcelle —
     * ce qui confond « le boîtier ne répond plus » et « cette parcelle n'a
     * jamais été instrumentée ». Deux situations qui n'appellent pas la même
     * intervention.
     */
    private void touch(IotDevice device, IngestReadingRequest request) {
        device.setLastSeenAt(Instant.now());

        if (request.getBatteryLevel() != null) {
            device.setBatteryLevel(request.getBatteryLevel());
        }
        if (request.getBatteryVoltage() != null) {
            device.setBatteryVoltage(request.getBatteryVoltage());
        }
        if (request.getFirmwareVersion() != null && !request.getFirmwareVersion().isBlank()) {
            device.setFirmwareVersion(request.getFirmwareVersion().trim());
        }

        device.setUpdatedAt(Instant.now());
        iotDeviceRepository.save(device);
    }

    /**
     * Établit le verdict de santé du boîtier et en tire les conséquences.
     *
     * <p><strong>Hors du chemin critique.</strong> L'analyse porte sur une
     * fenêtre de relevés et peut échouer pour mille raisons ; aucune ne justifie
     * de perdre la mesure qu'on vient de recevoir. En cas de défaut, le verdict
     * retombe sur {@code SAINE} — on ne déclare pas une panne sur une absence
     * d'information — et l'ingestion se poursuit.
     */
    private SensorHealthAnalyzer.Verdict assessHealth(IotDevice device) {
        try {
            SensorHealthAnalyzer.Verdict verdict = sensorHealthAnalyzer.analyze(device);

            device.setSensorHealth(verdict.health().name());
            device.setSensorHealthReason(verdict.reason());
            device.setSensorHealthCheckedAt(Instant.now());
            iotDeviceRepository.save(device);

            // L'alerte technique suit le verdict dans les deux sens : une sonde
            // remplacée referme son signalement. Sans cela, le technicien
            // apprendrait à ignorer une liste qui ne se vide jamais.
            alertService.raiseTechnical(device.getPlot(), device.getTechnicalId(),
                    verdict.reason(), verdict.blocksDiagnosis());

            return verdict;

        } catch (Exception e) {
            log.warn("Analyse de santé du boîtier {} en échec : {}",
                    device.getTechnicalId(), e.getMessage());
            return SensorHealthAnalyzer.Verdict.SOUND;
        }
    }

    /**
     * Signale qu'un boîtier a donné signe de vie sans déposer de mesure.
     *
     * C'est l'appel de liveness : sans lui, un boîtier qui répond mais dont
     * toutes les sondes sont débranchées serait compté comme muet.
     */
    @Override
    @Transactional
    public boolean touchByTechnicalId(String technicalId) {
        return iotDeviceRepository.findByTechnicalId(technicalId)
                .map(device -> {
                    device.setLastSeenAt(Instant.now());
                    device.setUpdatedAt(Instant.now());
                    iotDeviceRepository.save(device);
                    return true;
                })
                .orElse(false);
    }

    // ============================================================
    // Diagnostic
    // ============================================================
    private IngestResult diagnose(Long plotId, SensorReading reading,
                                  PlausibilityChecker.Verdict verdict,
                                  IngestResult.IngestResultBuilder result) {

        Optional<String> skip = reasonToSkip(plotId, reading, verdict);
        if (skip.isPresent()) {
            return result.diagnosed(false)
                    .skipReason(SKIP_STABLE)
                    .message(skip.get())
                    .recommendationCount(0)
                    .build();
        }

        try {
            // La culture est déduite de la culture en cours sur la parcelle.
            //
            // asDevice : le diagnostic traverse AccessGuard, qui exigeait un utilisateur
            // authentifié. L'ingestion n'en a pas — elle s'authentifie par clé partagée —
            // et TOUS les diagnostics capteur échouaient donc en production dès que le
            // cloisonnement était actif.
            DiagnosisResult diagnosis = MachineContext.asDevice(() ->
                    diagnosisService.diagnoseFromSensorReading(plotId, null, reading.getId()));

            return result
                    .diagnosed(true)
                    .diagnosis(diagnosis.getResult())
                    .recommendationCount(diagnosis.getRecommendations() == null
                            ? 0 : diagnosis.getRecommendations().size())
                    .build();

        } catch (ResourceNotFoundException e) {
            // Parcelle sans culture déclarée, ou relevé introuvable : le contexte
            // manque, pas le service.
            log.warn("Relevé {} conservé, diagnostic impossible : {}", reading.getId(), e.getMessage());
            return result.diagnosed(false)
                    .skipReason(SKIP_CONTEXT)
                    .message(e.getMessage())
                    .recommendationCount(0)
                    .build();

        } catch (ServiceUnavailableException e) {
            log.warn("Relevé {} conservé, service d'analyse indisponible : {}", reading.getId(), e.getMessage());
            return result.diagnosed(false)
                    .skipReason(SKIP_ML)
                    .message("Diagnostic momentanément indisponible. Le relevé est conservé.")
                    .recommendationCount(0)
                    .build();

        } catch (Exception e) {
            log.error("Relevé {} conservé, diagnostic en échec", reading.getId(), e);
            return result.diagnosed(false)
                    .skipReason(SKIP_ML)
                    .message("Diagnostic momentanément indisponible. Le relevé est conservé.")
                    .recommendationCount(0)
                    .build();
        }
    }

    /**
     * Décide s'il faut renoncer au diagnostic.
     *
     * Deux échappatoires priment sur le régulateur : une anomalie matérielle,
     * qui doit être signalée sans attendre, et la désactivation explicite. Le
     * reste est délégué à {@link DiagnosisThrottle} — jusqu'ici jamais appelé,
     * si bien qu'un boîtier émettant toutes les dix secondes déclenchait autant
     * d'appels au modèle et de séries de recommandations identiques.
     */
    private Optional<String> reasonToSkip(Long plotId, SensorReading reading,
                                          PlausibilityChecker.Verdict verdict) {
        if (!diagnosisConfig.getThrottle().isEnabled() || verdict.implausible()) {
            return Optional.empty();
        }
        return diagnosisThrottle.reasonToSkip(plotId, reading);
    }
}
