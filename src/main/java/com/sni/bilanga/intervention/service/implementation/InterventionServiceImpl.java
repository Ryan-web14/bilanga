package com.sni.bilanga.intervention.service.implementation;

import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.enums.RecommendationStatus;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.repository.CropRepository;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.intervention.dto.request.InterventionRequest;
import com.sni.bilanga.intervention.dto.response.InterventionEffect;
import com.sni.bilanga.intervention.dto.response.InterventionResponse;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.intervention.repository.InterventionRepository;
import com.sni.bilanga.intervention.service.interfaces.InterventionService;
import com.sni.bilanga.intervention.service.support.EffectAnalyzer;
import com.sni.bilanga.intervention.service.support.InterventionMapper;
import com.sni.bilanga.security.access.AccessGuard;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import com.sni.bilanga.utils.format.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Enregistrement des actions menées au champ.
 *
 * <p>Calqué sur {@code ObservationServiceImpl}, le modèle « CRUD scopé par
 * parcelle » du dépôt : l'accès passe par {@code PlotService.require}, qui
 * applique déjà le cloisonnement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionServiceImpl implements InterventionService {

    private final InterventionRepository interventionRepository;
    private final RecommendationRepository recommendationRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final PlotService plotService;
    private final CropService cropService;
    private final InterventionMapper mapper;
    private final EffectAnalyzer effectAnalyzer;
    private final AccessGuard accessGuard;

    @Override
    @Transactional
    public InterventionResponse create(InterventionRequest request) {
        Plot plot = plotService.require(request.getPlotId());

        Intervention intervention = Intervention.builder()
                .plot(plot)
                // La culture est déduite de la plantation en cours : la faire
                // saisir reviendrait à demander ce que le système sait déjà, et
                // à ouvrir la porte à une incohérence entre les deux.
                .crop(resolveCrop(request, plot))
                .recommendation(resolveRecommendation(request, plot))
                .type(DomainEnums.nameOf(request.getType()))
                .product(trimmed(request.getProduct()))
                .dose(request.getDose())
                .unit(trimmed(request.getUnit()))
                .cost(request.getCost())
                .performedAt(request.getPerformedAt() == null ? Instant.now() : request.getPerformedAt())
                .performedBy(resolveActor(request))
                .weatherNote(trimmed(request.getWeatherNote()))
                .note(request.getNote())
                .build();

        Intervention saved = interventionRepository.save(intervention);

        // Bouclage : déclarer avoir suivi un conseil suffit à le marquer appliqué.
        // Sans cela, l'exploitant devait le faire deux fois — et ne le faisait
        // qu'une, ce qui rendait le taux d'application faux à la baisse.
        markRecommendationApplied(saved);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InterventionResponse update(Long id, InterventionRequest request) {
        Intervention intervention = require(id);

        if (request.getPlotId() != null) {
            intervention.setPlot(plotService.require(request.getPlotId()));
        }
        intervention.setCrop(resolveCrop(request, intervention.getPlot()));
        intervention.setRecommendation(resolveRecommendation(request, intervention.getPlot()));
        intervention.setType(DomainEnums.nameOf(request.getType()));
        intervention.setProduct(trimmed(request.getProduct()));
        intervention.setDose(request.getDose());
        intervention.setUnit(trimmed(request.getUnit()));
        intervention.setCost(request.getCost());
        if (request.getPerformedAt() != null) {
            intervention.setPerformedAt(request.getPerformedAt());
        }
        intervention.setWeatherNote(trimmed(request.getWeatherNote()));
        intervention.setNote(request.getNote());
        intervention.setUpdatedAt(Instant.now());

        Intervention saved = interventionRepository.save(intervention);
        markRecommendationApplied(saved);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InterventionResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InterventionResponse> search(Long plotId, Long cropId, InterventionType type,
                                             Instant from, Instant to, Pageable pageable) {
        return interventionRepository
                .search(plotId, cropId, DomainEnums.nameOf(type),
                        TimeRange.from(from), TimeRange.to(to), pageable)
                .map(mapper::toResponse);
    }

    /**
     * Suppression réelle, et non archivage.
     *
     * Contrairement à une parcelle, une intervention mal saisie n'a aucune
     * valeur historique : la conserver fausserait les coûts agrégés et
     * l'analyse d'effet, qui sont précisément ce à quoi elle sert.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        interventionRepository.delete(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public InterventionEffect effect(Long id) {
        return effectAnalyzer.analyze(require(id));
    }

    // ============================================================
    // Résolution du contexte
    // ============================================================
    private Crop resolveCrop(InterventionRequest request, Plot plot) {
        if (request.getCropId() != null) {
            Crop crop = cropRepository.findById(request.getCropId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Culture introuvable : " + request.getCropId()));

            if (!crop.getPlot().getId().equals(plot.getId())) {
                throw new BusinessRuleException(
                        "Cette culture appartient à une autre parcelle.");
            }
            return crop;
        }
        // Nulle si la parcelle n'a rien en cours : un désherbage d'inter-campagne
        // est une intervention parfaitement légitime sans culture rattachée.
        return cropService.findActiveCrop(plot.getId()).orElse(null);
    }

    private Recommendation resolveRecommendation(InterventionRequest request, Plot plot) {
        if (request.getRecommendationId() == null) {
            return null;
        }

        Recommendation recommendation = recommendationRepository.findById(request.getRecommendationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conseil introuvable : " + request.getRecommendationId()));

        // Rattacher une action à un conseil d'une autre parcelle fausserait à la
        // fois le taux d'application et l'analyse d'effet.
        Long recommendationPlotId = recommendation.getDiagnostic() == null
                || recommendation.getDiagnostic().getPlot() == null
                ? null
                : recommendation.getDiagnostic().getPlot().getId();

        if (recommendationPlotId != null && !recommendationPlotId.equals(plot.getId())) {
            throw new BusinessRuleException(
                    "Ce conseil porte sur une autre parcelle.");
        }
        return recommendation;
    }

    private Users resolveActor(InterventionRequest request) {
        Long userId = request.getPerformedById() != null
                ? request.getPerformedById()
                : accessGuard.currentUserId();

        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * Bascule le conseil suivi en {@code APPLIQUEE}.
     *
     * <p>Un conseil déjà tranché n'est pas retouché : la trace de la décision
     * d'origine et de sa date resterait ambiguë — c'est la même règle que celle
     * appliquée au retour manuel dans {@code RecommendationServiceImpl}.
     */
    private void markRecommendationApplied(Intervention intervention) {
        Recommendation recommendation = intervention.getRecommendation();
        if (recommendation == null) {
            return;
        }

        RecommendationStatus current = RecommendationStatus.from(recommendation.getStatus());
        if (current != null && current.isFinal()) {
            return;
        }

        recommendation.setStatus(RecommendationStatus.APPLIQUEE.name());
        recommendation.setFeedbackAt(Instant.now());
        if (recommendation.getFeedbackNote() == null) {
            recommendation.setFeedbackNote("Appliqué : intervention " + intervention.getId()
                                           + " du " + intervention.getPerformedAt() + ".");
        }
        recommendationRepository.save(recommendation);
    }

    private Intervention require(Long id) {
        Intervention intervention = interventionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable : " + id));

        // Passage par PlotService pour bénéficier du cloisonnement, comme partout
        // ailleurs : le contrôle est écrit une fois et couvre tous les domaines.
        plotService.require(intervention.getPlot().getId());
        return intervention;
    }

    private String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
