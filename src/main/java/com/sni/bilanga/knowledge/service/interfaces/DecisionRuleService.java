package com.sni.bilanga.knowledge.service.interfaces;


import com.sni.bilanga.knowledge.dto.request.ArbitrationRequest;
import com.sni.bilanga.knowledge.dto.request.CorrelationRuleRequest;
import com.sni.bilanga.knowledge.dto.request.KnowledgeRuleRequest;
import com.sni.bilanga.knowledge.model.CorrelationRule;
import com.sni.bilanga.knowledge.model.KnowledgeRule;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;

import com.sni.bilanga.knowledge.dto.response.*;
import java.util.List;

/**
 * Administration des trois familles de règles : conseils attachés à un
 * diagnostic capteur, corrélations entre maladie et mesures, et arbitrages
 * entre conseils qui se contrarient.
 */
public interface DecisionRuleService {

    // --- Règles attachées à un diagnostic capteur ---
    KnowledgeRuleResponse createRule(KnowledgeRuleRequest request);

    KnowledgeRuleResponse updateRule(Long id, KnowledgeRuleRequest request);

    List<KnowledgeRuleResponse> findRules(String category);

    void deleteRule(Long id);

    // --- Corrélations maladie / mesures ---
    CorrelationRuleResponse createCorrelation(CorrelationRuleRequest request);

    CorrelationRuleResponse updateCorrelation(Long id, CorrelationRuleRequest request);

    List<CorrelationRuleResponse> findCorrelations();

    void deleteCorrelation(Long id);

    // --- Arbitrages ---
    ArbitrationResponse createArbitration(ArbitrationRequest request);

    ArbitrationResponse updateArbitration(Long id, ArbitrationRequest request);

    List<ArbitrationResponse> findArbitrations();

    void deleteArbitration(Long id);
}
