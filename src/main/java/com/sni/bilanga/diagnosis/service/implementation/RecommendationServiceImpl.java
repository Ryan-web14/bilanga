package com.sni.bilanga.diagnosis.service.implementation;

import com.sni.bilanga.diagnosis.dto.request.RecommendationFeedbackRequest;
import com.sni.bilanga.diagnosis.dto.response.RecommendationResponse;
import com.sni.bilanga.diagnosis.dto.response.RecommendationUptake;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.diagnosis.service.interfaces.RecommendationService;
import com.sni.bilanga.diagnosis.service.support.DiagnosisMapper;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationStatus;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.utils.format.TimeRange;
import com.sni.bilanga.utils.sort.SemanticSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final Map<String, String> SORTABLE_RANKS = Map.of(
            "priority", SemanticSort.rankExpression("r.priority", "HAUTE", "MOYENNE", "BASSE"),
            "status",   SemanticSort.rankExpression("r.status", "ACTIVE", "APPLIQUEE", "IGNOREE"));

    private final RecommendationRepository recommendationRepository;
    private final DiagnosisMapper diagnosisMapper;

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse findById(Long id) {
        return diagnosisMapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecommendationResponse> search(Long plotId, Long diagnosticId,
                                               RecommendationStatus status,
                                               RecommendationPriority priority,
                                               RecommendationType type,
                                               Instant from, Instant to, Pageable pageable) {
        return recommendationRepository
                .search(plotId, diagnosticId, DomainEnums.nameOf(status), DomainEnums.nameOf(priority),
                        DomainEnums.nameOf(type), TimeRange.from(from), TimeRange.to(to),
                        SemanticSort.rewrite(pageable, SORTABLE_RANKS))
                .map(diagnosisMapper::toResponse);
    }

    @Override
    @Transactional
    public RecommendationResponse recordFeedback(Long id, RecommendationFeedbackRequest request) {
        Recommendation recommendation = require(id);

        RecommendationStatus current = RecommendationStatus.from(recommendation.getStatus());
        RecommendationStatus target = request.getStatus();

        // Un conseil déjà tranché ne se retranche pas : la trace de la décision
        // d'origine, et de sa date, resterait ambiguë.
        if (current != null && current.isFinal()) {
            throw BusinessRuleException.invalidTransition(
                    "Conseil " + id, current.getLabel(), target.getLabel());
        }
        if (target == RecommendationStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Un conseil ne peut pas être remis à l'état « à traiter ».");
        }

        recommendation.setStatus(target.name());
        recommendation.setFeedbackAt(Instant.now());
        recommendation.setFeedbackNote(request.getNote());

        return diagnosisMapper.toResponse(recommendationRepository.save(recommendation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationUptake> uptake(Long plotId) {
        List<RecommendationUptake> uptakes = new ArrayList<>();

        for (Object[] row : recommendationRepository.uptakeByType(plotId)) {
            String type = (String) row[0];
            long total = toLong(row[1]);
            long applied = toLong(row[2]);
            long ignored = toLong(row[3]);
            long answered = applied + ignored;

            RecommendationType parsed = RecommendationType.from(type);

            uptakes.add(RecommendationUptake.builder()
                    .recommendationType(type)
                    .recommendationTypeLabel(parsed == null ? null : parsed.getLabel())
                    .total(total)
                    .applied(applied)
                    .ignored(ignored)
                    .pending(total - answered)
                    // Un taux calculé sur zéro réponse ne veut rien dire : mieux
                    // vaut ne rien afficher que d'afficher 0 %.
                    .applicationRate(answered == 0 ? null : round(applied / (double) answered))
                    .build());
        }
        return uptakes;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private Recommendation require(Long id) {
        return recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conseil introuvable : " + id));
    }
}
