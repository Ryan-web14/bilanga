package com.sni.bilanga.knowledge.service.interfaces;


import com.sni.bilanga.knowledge.dto.request.DiseaseKnowledgeRequest;
import com.sni.bilanga.knowledge.dto.request.DiseaseRiskConditionRequest;
import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import com.sni.bilanga.knowledge.model.DiseaseRiskCondition;

import com.sni.bilanga.knowledge.dto.response.*;
import java.util.List;

public interface DiseaseKnowledgeService {

    // --- Connaissances maladie ---
    DiseaseKnowledgeResponse create(DiseaseKnowledgeRequest request);

    DiseaseKnowledgeResponse update(Long id, DiseaseKnowledgeRequest request);

    DiseaseKnowledgeResponse findById(Long id);

    List<DiseaseKnowledgeResponse> findAll(String cropName);

    void delete(Long id);

    // --- Conditions d'apparition ---
    DiseaseRiskConditionResponse createCondition(DiseaseRiskConditionRequest request);

    DiseaseRiskConditionResponse updateCondition(Long id, DiseaseRiskConditionRequest request);

    List<DiseaseRiskConditionResponse> findConditions(String cropName);

    void deleteCondition(Long id);
}
