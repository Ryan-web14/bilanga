package com.sni.bilanga.iot.service.interfaces;


import com.sni.bilanga.enums.HistoryGranularity;
import com.sni.bilanga.enums.ReadingQuality;
import com.sni.bilanga.iot.dto.request.SensorReadingRequest;
import com.sni.bilanga.iot.dto.response.PlotHistoryResponse;
import com.sni.bilanga.iot.dto.response.SensorReadingResponse;
import com.sni.bilanga.iot.model.SensorReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

public interface SensorReadingService {

    SensorReadingResponse create(SensorReadingRequest request);

    SensorReadingResponse findById(Long id);

    /**
     * Recherche paginée sur la série temporelle. Remplace {@code findByPlot},
     * qui ramenait tout l'historique de la parcelle.
     */
    Page<SensorReadingResponse> search(Long plotId, Long deviceId, Instant from, Instant to,
                                       boolean anomalyOnly, ReadingQuality quality, Pageable pageable);

    void delete(Long id);

    SensorReading require(Long id);

    /** Dernier relevé de la parcelle : support de la corrélation automatique. */
    Optional<SensorReading> findLatest(Long plotId);

    /**
     * Série temporelle agrégée : min, moyenne et max par intervalle.
     *
     * Évite au client de rapatrier des milliers de relevés bruts pour tracer
     * une courbe — l'agrégation est faite par la base.
     */
    PlotHistoryResponse history(Long plotId, Instant from, Instant to,
                                HistoryGranularity granularity);
}
