package com.sni.bilanga.harvest.service.implementation;

import com.sni.bilanga.enums.AccessScope;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.CropRepository;
import com.sni.bilanga.farm.repository.PlotRepository;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.security.access.AccessGuard;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.harvest.dto.request.HarvestRequest;
import com.sni.bilanga.harvest.dto.response.HarvestResponse;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.model.Harvest;
import com.sni.bilanga.harvest.repository.HarvestRepository;
import com.sni.bilanga.harvest.service.interfaces.HarvestService;
import com.sni.bilanga.harvest.service.support.HarvestMapper;
import com.sni.bilanga.harvest.service.support.MarginCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HarvestServiceImpl implements HarvestService {

    /**
     * Bornes par défaut du bilan.
     *
     * Une campagne dure de quatre mois (tomate) à onze (manioc) : deux ans en
     * arrière couvrent largement la plus longue, sans ramener un historique
     * qu'on ne regardera pas.
     */
    private static final int DEFAULT_LOOKBACK_YEARS = 2;

    private final HarvestRepository harvestRepository;
    private final CropRepository cropRepository;
    private final PlotRepository plotRepository;
    private final PlotService plotService;
    private final CropService cropService;
    private final HarvestMapper mapper;
    private final MarginCalculator marginCalculator;
    private final AccessGuard accessGuard;

    @Override
    @Transactional
    public HarvestResponse create(HarvestRequest request) {
        Plot plot = plotService.require(request.getPlotId());

        Harvest harvest = Harvest.builder()
                .plot(plot)
                .crop(resolveCrop(request, plot))
                .quantity(request.getQuantity())
                .unit(trimmed(request.getUnit()))
                .quality(DomainEnums.nameOf(request.getQuality()))
                .harvestedAt(request.getHarvestedAt() == null ? LocalDate.now() : request.getHarvestedAt())
                .unitPrice(request.getUnitPrice())
                .currency(request.getCurrency() == null || request.getCurrency().isBlank()
                        ? "XAF" : request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT))
                .note(request.getNote())
                .build();

        return mapper.toResponse(harvestRepository.save(harvest));
    }

    @Override
    @Transactional
    public HarvestResponse update(Long id, HarvestRequest request) {
        Harvest harvest = require(id);

        if (request.getPlotId() != null) {
            harvest.setPlot(plotService.require(request.getPlotId()));
        }
        harvest.setCrop(resolveCrop(request, harvest.getPlot()));
        harvest.setQuantity(request.getQuantity());
        harvest.setUnit(trimmed(request.getUnit()));
        harvest.setQuality(DomainEnums.nameOf(request.getQuality()));
        if (request.getHarvestedAt() != null) {
            harvest.setHarvestedAt(request.getHarvestedAt());
        }
        harvest.setUnitPrice(request.getUnitPrice());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            harvest.setCurrency(request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        }
        harvest.setNote(request.getNote());
        harvest.setUpdatedAt(Instant.now());

        return mapper.toResponse(harvestRepository.save(harvest));
    }

    @Override
    @Transactional(readOnly = true)
    public HarvestResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HarvestResponse> search(Long plotId, Long cropId, LocalDate from, LocalDate to,
                                        Pageable pageable) {
        return harvestRepository
                .search(plotId, cropId, defaultFrom(from), defaultTo(to), pageable)
                .map(mapper::toResponse);
    }

    /**
     * Suppression réelle : une récolte mal saisie fausse la marge, et c'est
     * précisément ce à quoi elle sert. La conserver « pour l'histoire »
     * dégraderait le seul chiffre que l'exploitant regarde.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        harvestRepository.delete(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PlotEconomics economics(Long plotId, Long cropId, LocalDate from, LocalDate to) {
        Plot plot = plotService.require(plotId);

        // Le propriétaire de la parcelle passe sans condition. Pour un membre
        // d'exploitation, le rôle décide : un technicien venu changer une sonde
        // n'a pas à connaître les marges, et un ouvrier non plus. Sans
        // exploitation rattachée, ce contrôle est inerte.
        accessGuard.requireScope(plot, AccessScope.ECONOMIQUE);

        Crop crop = cropId != null
                ? cropRepository.findById(cropId)
                        .orElseThrow(() -> new ResourceNotFoundException("Culture introuvable : " + cropId))
                // Sans campagne désignée, celle en cours : c'est celle dont
                // l'exploitant veut le bilan neuf fois sur dix.
                : cropService.findActiveCrop(plotId).orElse(null);

        if (crop != null && !crop.getPlot().getId().equals(plotId)) {
            throw new BusinessRuleException("Cette culture appartient à une autre parcelle.");
        }

        return marginCalculator.compute(plot, crop, defaultFrom(from), defaultTo(to));
    }

    /**
     * Bilan de chaque parcelle, trié par marge à l'hectare.
     *
     * <p>Le tri porte sur la marge <em>à l'hectare</em> et non sur la marge
     * brute : classer une parcelle de dix hectares devant une d'un demi-hectare
     * ne dit rien d'autre que leurs tailles respectives. Les parcelles sans
     * surface renseignée passent en fin de liste, faute de pouvoir être
     * comparées — leur bilan reste consultable, il n'est simplement pas classé.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PlotEconomics> economicsForAllPlots(Long userId, LocalDate from, LocalDate to) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);

        return plotRepository.findAll().stream()
                .filter(plot -> !PlotStatus.ARCHIVEE.name().equalsIgnoreCase(plot.getStatus()))
                .filter(plot -> matchesOwner(plot, accessGuard.resolveOwnerFilter(userId)))
                // Une parcelle dont le rôle n'ouvre pas l'économique est écartée
                // de la comparaison plutôt que de la faire échouer : un
                // conseiller doit pouvoir consulter la liste, amputée de ce
                // qu'il n'a pas à voir.
                .filter(plot -> accessGuard.canRead(plot, AccessScope.ECONOMIQUE))
                .map(plot -> marginCalculator.compute(
                        plot, cropService.findActiveCrop(plot.getId()).orElse(null), start, end))
                .sorted(Comparator.comparing(
                        PlotEconomics::getMarginPerHectare,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean matchesOwner(Plot plot, Long ownerFilter) {
        if (ownerFilter == null) {
            return true;
        }
        return plot.getUser() != null && ownerFilter.equals(plot.getUser().getId());
    }

    // ============================================================
    // Interne
    // ============================================================
    private Crop resolveCrop(HarvestRequest request, Plot plot) {
        Crop crop = request.getCropId() != null
                ? cropRepository.findById(request.getCropId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Culture introuvable : " + request.getCropId()))
                : cropService.findActiveCrop(plot.getId()).orElse(null);

        if (crop == null) {
            // Contrairement à une intervention, une récolte sans campagne
            // n'entre dans aucun calcul de marge : l'accepter reviendrait à
            // enregistrer une donnée qui ne servira jamais.
            throw new BusinessRuleException(
                    "Aucune culture en cours sur cette parcelle. Précisez la campagne récoltée : "
                    + "sans elle, la récolte ne peut être rattachée à aucun bilan.");
        }
        if (!crop.getPlot().getId().equals(plot.getId())) {
            throw new BusinessRuleException("Cette culture appartient à une autre parcelle.");
        }
        return crop;
    }

    private Harvest require(Long id) {
        Harvest harvest = harvestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Récolte introuvable : " + id));

        // Cloisonnement par le point de passage habituel.
        plotService.require(harvest.getPlot().getId());
        return harvest;
    }

    private LocalDate defaultFrom(LocalDate from) {
        return from == null ? LocalDate.now().minusYears(DEFAULT_LOOKBACK_YEARS) : from;
    }

    private LocalDate defaultTo(LocalDate to) {
        return to == null ? LocalDate.now() : to;
    }

    private String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
