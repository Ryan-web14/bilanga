package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.model.CorrelationRule;
import com.sni.bilanga.knowledge.repository.CorrelationRuleRepository;
import com.sni.bilanga.enums.RecommendationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CorrelationEngine {

    private final CorrelationRuleRepository correlationRuleRepository;

    public List<RecommendationItem> evaluate(String cropName, String diseaseCode, SensorReading reading) {
        List<RecommendationItem> results = new ArrayList<>();
        if (reading == null) {
            return results;
        }

        List<CorrelationRule> candidates =
                correlationRuleRepository.findCandidates(cropName, diseaseCode);

        for (CorrelationRule rule : candidates) {
            Double measured = extractMeasure(reading, rule.getMeasureField());
            if (measured != null && matches(measured, rule.getOperator(), rule.getThreshold())) {
                results.add(RecommendationItem.builder()
                        .content(rule.getExtraRecommendation())
                        .type(RecommendationType.CORRELATION.name())
                        .priority(rule.getPriority())
                        .category("MALADIE_FOLIAIRE")
                        .sourceRuleId(rule.getId())
                        .measureField(rule.getMeasureField())
                        .observedValue(measured)
                        .thresholdValue(rule.getThreshold())
                        .build());
            }
        }
        return results;
    }

    private Double extractMeasure(SensorReading r, String field) {
        return switch (field) {
            case "temperature"  -> r.getTemperature();
            case "humidite_sol" -> r.getHumiditeSol();
            case "humidite_air" -> r.getHumiditeAir();
            case "ph"           -> r.getPh();
            case "azote"        -> r.getAzote();
            case "phosphore"    -> r.getPhosphore();
            case "potassium"    -> r.getPotassium();
            case "luminosite"   -> r.getLuminosite();
            default -> null;
        };
    }

    private boolean matches(double value, String operator, double threshold) {
        return switch (operator) {
            case ">"  -> value >  threshold;
            case "<"  -> value <  threshold;
            case ">=" -> value >= threshold;
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            default   -> false;
        };
    }
}
