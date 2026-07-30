package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;
import com.sni.bilanga.knowledge.repository.RecommendationArbitrationRepository;
import com.sni.bilanga.enums.RecommendationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Concilie les conseils issus de domaines qui se contrarient à la lecture.
 *
 * Deux recommandations peuvent être justes chacune de son côté et pourtant
 * dérouter mises côte à côte : réduire l'humidité pour contenir une maladie
 * foliaire, irriguer pour lever un stress hydrique. L'une vise l'air, l'autre
 * le sol, et la contradiction n'est qu'apparente.
 *
 * Le moteur ne supprime aucun conseil : il ajoute la synthèse qu'un agronome
 * formulerait, laquelle indique comment appliquer les deux ensemble.
 */
@Component
@RequiredArgsConstructor
public class ConflictArbitrator {

    private final RecommendationArbitrationRepository arbitrationRepository;

    public List<RecommendationItem> arbitrate(String cropName, List<RecommendationItem> items) {
        List<RecommendationItem> syntheses = new ArrayList<>();
        if (cropName == null || items == null || items.size() < 2) {
            return syntheses;
        }

        Set<String> present = new HashSet<>();
        for (RecommendationItem item : items) {
            if (item.getCategory() != null) {
                present.add(item.getCategory());
            }
        }

        for (RecommendationArbitration rule : arbitrationRepository.findForCrop(cropName)) {
            if (present.contains(rule.getCategoryA()) && present.contains(rule.getCategoryB())) {
                syntheses.add(RecommendationItem.builder()
                        .content(rule.getSynthesis())
                        .type(RecommendationType.ARBITRAGE.name())
                        .priority(rule.getPriority())
                        .category(rule.getCategoryA() + "+" + rule.getCategoryB())
                        .build());
            }
        }
        return syntheses;
    }
}