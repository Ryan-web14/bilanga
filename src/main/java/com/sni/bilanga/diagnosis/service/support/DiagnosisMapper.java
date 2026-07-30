package com.sni.bilanga.diagnosis.service.support;


import com.sni.bilanga.diagnosis.dto.response.DiagnosticHistoryResponse;
import com.sni.bilanga.diagnosis.dto.response.RecommendationResponse;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationStatus;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.enums.Culture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DiagnosisMapper {

    private final ConfidenceEvaluator confidenceEvaluator;

    public DiagnosticHistoryResponse toHistory(Diagnostic d, List<Recommendation> recommendations) {
        return DiagnosticHistoryResponse.builder()
                .id(d.getId())
                .plotId(d.getPlot().getId())
                .plotName(d.getPlot().getName())
                .source(d.getSource())
                .result(d.getResult())
                .confidenceScore(d.getConfidenceScore())
                .confidenceLevel(confidenceEvaluator.level(d.getConfidenceScore()))
                .cropName(Culture.canonical(d.getCropName()))
                .imageUrl(d.getImageUrl())
                .readingId(d.getReading() == null ? null : d.getReading().getId())
                .modelName(d.getAiModel() == null ? null : d.getAiModel().getName())
                .diagnosedAt(d.getDiagnosedAt())
                .recommendations(recommendations.stream().map(this::toResponse).toList())
                .build();
    }

    public RecommendationResponse toResponse(Recommendation r) {
        RecommendationType type = RecommendationType.from(r.getRecommendationType());
        RecommendationPriority priority = RecommendationPriority.from(r.getPriority());
        RecommendationStatus status = RecommendationStatus.from(r.getStatus());

        return RecommendationResponse.builder()
                .id(r.getId())
                .diagnosticId(r.getDiagnostic() == null ? null : r.getDiagnostic().getId())
                .plotId(r.getDiagnostic() == null || r.getDiagnostic().getPlot() == null
                        ? null : r.getDiagnostic().getPlot().getId())
                .content(r.getContent())
                .recommendationType(r.getRecommendationType())
                .recommendationTypeLabel(type == null ? null : type.getLabel())
                .priority(r.getPriority())
                .priorityLabel(priority == null ? null : priority.getLabel())
                .status(r.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .sourceRuleId(r.getSourceRuleId())
                .measureField(r.getMeasureField())
                .observedValue(r.getObservedValue())
                .thresholdValue(r.getThresholdValue())
                .estimatedCost(r.getEstimatedCost())
                .createdAt(r.getCreatedAt())
                .feedbackAt(r.getFeedbackAt())
                .feedbackNote(r.getFeedbackNote())
                .build();
    }
}
