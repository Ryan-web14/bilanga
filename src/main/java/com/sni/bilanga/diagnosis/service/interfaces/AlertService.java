package com.sni.bilanga.diagnosis.service.interfaces;


import com.sni.bilanga.diagnosis.dto.response.AlertResponse;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.AlertStatus;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface AlertService {

    /**
     * Confronte l'état des alertes de la parcelle au diagnostic qui vient d'être
     * produit : lève ce qui doit l'être, rafraîchit ou fait monter ce qui dure,
     * et referme ce que la situation ne justifie plus.
     */
    void raiseIfNeeded(Diagnostic diagnostic, List<RecommendationItem> recommendations, boolean reliable);

    List<AlertResponse> findByPlot(Long plotId, boolean openOnly);

    List<AlertResponse> findOpen();

    Page<AlertResponse> search(Long plotId, com.sni.bilanga.enums.AlertCategory category,
                               AlertLevel level, AlertStatus status, boolean openOnly,
                               Instant from, Instant to, Pageable pageable);

    AlertResponse findById(Long id);

    AlertResponse acknowledge(Long id);

    AlertResponse resolve(Long id);

    /**
     * Désigne le responsable du traitement et, le cas échéant, le terme.
     *
     * <p>Une alerte sans destinataire reste dans la liste de tout le monde,
     * donc dans celle de personne. {@code userId} nul retire l'affectation.
     */
    AlertResponse assign(Long id, Long userId, java.time.Instant dueAt);

    /**
     * Lève — ou referme — une alerte de panne matérielle.
     *
     * <p>Distincte des alertes agronomiques : elle s'adresse au technicien et
     * dit quelle sonde changer, non ce qu'il faudrait faire au champ. Les mêler
     * conduirait chacun à filtrer celles de l'autre.
     *
     * @param deviceKey identifiant technique du boîtier, qui compose l'empreinte
     *                  de déduplication — une sonde en panne depuis trois jours
     *                  produit une alerte, pas trois cents
     * @param faulty    faux lorsque la panne a cessé : l'alerte est alors
     *                  refermée, sans quoi une sonde remplacée laisserait un
     *                  signalement que plus rien ne justifie
     */
    void raiseTechnical(com.sni.bilanga.farm.model.Plot plot, String deviceKey,
                        String reason, boolean faulty);
}
