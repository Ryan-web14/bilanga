package com.sni.bilanga.harvest.service.interfaces;

import com.sni.bilanga.harvest.dto.request.HarvestRequest;
import com.sni.bilanga.harvest.dto.response.HarvestResponse;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface HarvestService {

    HarvestResponse create(HarvestRequest request);

    HarvestResponse update(Long id, HarvestRequest request);

    HarvestResponse findById(Long id);

    Page<HarvestResponse> search(Long plotId, Long cropId, LocalDate from, LocalDate to,
                                 Pageable pageable);

    void delete(Long id);

    /**
     * Bilan économique de la parcelle : produit, charges, marge, rendement.
     *
     * <p>Entièrement recalculé à la demande depuis les récoltes, les
     * interventions et la surface de la culture — rien n'est stocké, un total
     * mis en cache divergeant dès la première correction de saisie.
     *
     * <p>La réponse porte sa propre réserve : la mise en regard du taux de suivi
     * des conseils et du rendement est descriptive, pas causale.
     *
     * @param cropId campagne visée ; sans lui, la période entière est agrégée
     */
    PlotEconomics economics(Long plotId, Long cropId, LocalDate from, LocalDate to);

    /**
     * Bilan de chaque parcelle, pour les comparer.
     *
     * <p>C'est la comparaison qui informe, pas le chiffre isolé : savoir qu'une
     * parcelle dégage 180 000 F de marge ne dit rien tant qu'on ignore ce que
     * font les autres. Les résultats sont triés par marge à l'hectare — le seul
     * critère comparable entre surfaces différentes.
     *
     * @param userId propriétaire ; le cloisonnement s'applique par-dessus
     */
    java.util.List<PlotEconomics> economicsForAllPlots(Long userId, LocalDate from, LocalDate to);
}
