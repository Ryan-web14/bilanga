package com.sni.bilanga.iot.service.interfaces;


import com.sni.bilanga.iot.dto.request.IngestBatchRequest;
import com.sni.bilanga.iot.dto.request.IngestReadingRequest;
import com.sni.bilanga.iot.dto.response.IngestBatchResult;
import com.sni.bilanga.iot.dto.response.IngestResult;

public interface IngestService {

    /**
     * Enregistre un relevé transmis par un boîtier et déclenche le diagnostic
     * dans la foulée.
     */
    IngestResult ingest(IngestReadingRequest request);

    /**
     * Écrit le relevé dans une transaction <strong>séparée</strong>, validée aussitôt.
     *
     * <p>Exposée sur l'interface parce qu'elle doit être appelée à travers le proxy —
     * une invocation interne court-circuiterait l'intercepteur transactionnel, et la
     * séparation n'aurait pas lieu. Ce n'est pas une opération métier ; ne l'appelez pas
     * ailleurs que depuis {@link #ingest}.
     *
     * <p>Elle porte l'invariant premier du système : perdre un diagnostic parce qu'un
     * service tiers est muet est acceptable, perdre une mesure ne l'est pas.
     */
    com.sni.bilanga.iot.model.SensorReading persistReading(
            com.sni.bilanga.iot.model.SensorReading reading);

    /**
     * Rejoue un lot de relevés tamponnés hors ligne.
     *
     * Chaque relevé est traité indépendamment : un échec n'interrompt pas le
     * lot et n'annule pas ce qui précède.
     */
    IngestBatchResult ingestBatch(IngestBatchRequest request);

    /**
     * Consigne qu'un boîtier a donné signe de vie sans déposer de mesure.
     *
     * <p>Appelée par la route de liveness. Sans elle, un boîtier qui répond mais
     * dont les sondes sont débranchées serait compté parmi les muets, et la
     * vue d'ensemble enverrait quelqu'un chercher une panne de communication
     * qui n'existe pas.
     *
     * @return faux si aucun boîtier ne porte cet identifiant technique
     */
    boolean touchByTechnicalId(String technicalId);
}
