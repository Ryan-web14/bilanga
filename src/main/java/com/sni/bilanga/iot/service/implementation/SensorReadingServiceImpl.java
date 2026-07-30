package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.HistoryGranularity;
import com.sni.bilanga.enums.ReadingQuality;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.dto.request.SensorReadingRequest;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.dto.response.PlotHistoryResponse;
import com.sni.bilanga.iot.dto.response.SensorReadingResponse;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import com.sni.bilanga.iot.service.interfaces.IotDeviceService;
import com.sni.bilanga.iot.service.interfaces.SensorReadingService;
import com.sni.bilanga.iot.service.support.IotMapper;
import com.sni.bilanga.iot.service.support.PlausibilityChecker;
import com.sni.bilanga.utils.format.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SensorReadingServiceImpl implements SensorReadingService {

    private final SensorReadingRepository sensorReadingRepository;
    private final PlotService plotService;
    private final IotDeviceService iotDeviceService;
    private final PlausibilityChecker plausibilityChecker;
    private final IotMapper mapper;

    @Override
    @Transactional
    public SensorReadingResponse create(SensorReadingRequest request) {
        SensorReading reading = SensorReading.builder()
                .plot(plotService.require(request.getPlotId()))
                .device(request.getDeviceId() == null ? null : iotDeviceService.require(request.getDeviceId()))
                .temperature(request.getTemperature())
                .temperatureSol(request.getTemperatureSol())
                .humiditeSol(request.getHumiditeSol())
                .humiditeAir(request.getHumiditeAir())
                .ph(request.getPh())
                .azote(request.getAzote())
                .phosphore(request.getPhosphore())
                .potassium(request.getPotassium())
                .luminosite(request.getLuminosite())
                .pluviometrie(request.getPluviometrie())
                .conductiviteElectrique(request.getConductiviteElectrique())
                .quality(DomainEnums.nameOf(
                        request.getQuality() == null ? ReadingQuality.MANUELLE : request.getQuality()))
                .build();

        reading.setAnomalyDetected(plausibilityChecker.check(reading).implausible());

        return mapper.toResponse(sensorReadingRepository.save(reading));
    }

    @Override
    @Transactional(readOnly = true)
    public SensorReadingResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SensorReadingResponse> search(Long plotId, Long deviceId, Instant from, Instant to,
                                              boolean anomalyOnly, ReadingQuality quality,
                                              Pageable pageable) {
        return sensorReadingRepository
                .search(plotId, deviceId, TimeRange.from(from), TimeRange.to(to),
                        anomalyOnly, DomainEnums.nameOf(quality), pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        sensorReadingRepository.delete(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SensorReading require(Long id) {
        return sensorReadingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relevé introuvable : " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SensorReading> findLatest(Long plotId) {
        return sensorReadingRepository.findFirstByPlot_IdOrderByRecordedAtDesc(plotId);
    }

    /** Ordre des mesures dans la requête d'agrégation — à garder synchronisé avec elle. */
    private static final List<String> AGGREGATED_MEASURES = List.of(
            "temperature", "humidite_sol", "humidite_air", "ph",
            "azote", "phosphore", "potassium", "luminosite");

    /** Index de la première colonne de statistiques : bucket, sample, anomaly la précèdent. */
    private static final int FIRST_STAT_COLUMN = 3;

    @Override
    @Transactional(readOnly = true)
    public PlotHistoryResponse history(Long plotId, Instant from, Instant to,
                                       HistoryGranularity granularity) {
        // require() porte le contrôle d'accès : l'historique d'une parcelle est
        // aussi sensible que la parcelle elle-même.
        Plot plot = plotService.require(plotId);

        HistoryGranularity unit = granularity == null ? HistoryGranularity.DAY : granularity;
        Instant start = TimeRange.from(from);
        Instant end = TimeRange.to(to);

        List<PlotHistoryResponse.HistoryPoint> points = new ArrayList<>();
        int totalReadings = 0;

        for (Object[] row : sensorReadingRepository.aggregateHistory(plotId, unit.getSqlUnit(), start, end)) {
            int sampleCount = intValue(row[1]);
            totalReadings += sampleCount;

            points.add(PlotHistoryResponse.HistoryPoint.builder()
                    .bucket(toInstant(row[0]))
                    .sampleCount(sampleCount)
                    .anomalyCount(intValue(row[2]))
                    .measures(statsOf(row))
                    .build());
        }

        return PlotHistoryResponse.builder()
                .plotId(plot.getId())
                .plotName(plot.getName())
                .from(start)
                .to(end)
                .granularity(unit.name())
                .granularityLabel(unit.getLabel())
                .bucketCount(points.size())
                .readingCount(totalReadings)
                .points(points)
                .build();
    }

    /**
     * Une mesure jamais renseignée sur l'intervalle est absente de la carte,
     * et non présente à zéro : « pas de sonde » et « valeur nulle » sont deux
     * informations différentes, que le client doit pouvoir distinguer.
     */
    private Map<String, PlotHistoryResponse.MeasureStats> statsOf(Object[] row) {
        Map<String, PlotHistoryResponse.MeasureStats> measures = new LinkedHashMap<>();

        for (int i = 0; i < AGGREGATED_MEASURES.size(); i++) {
            int offset = FIRST_STAT_COLUMN + (i * 3);
            Double min = doubleValue(row[offset]);
            Double avg = doubleValue(row[offset + 1]);
            Double max = doubleValue(row[offset + 2]);

            if (min == null && avg == null && max == null) {
                continue;
            }
            measures.put(AGGREGATED_MEASURES.get(i),
                    new PlotHistoryResponse.MeasureStats(round(min), round(avg), round(max)));
        }
        return measures;
    }

    private Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case java.sql.Timestamp timestamp -> timestamp.toInstant();
            case null, default -> null;
        };
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 100d) / 100d;
    }
}
