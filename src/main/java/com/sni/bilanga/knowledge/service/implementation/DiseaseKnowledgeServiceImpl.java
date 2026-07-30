package com.sni.bilanga.knowledge.service.implementation;


import com.sni.bilanga.config.EvictsKnowledgeCaches;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.knowledge.dto.request.DiseaseKnowledgeRequest;
import com.sni.bilanga.knowledge.dto.request.DiseaseRiskConditionRequest;
import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import com.sni.bilanga.knowledge.model.DiseaseRiskCondition;
import com.sni.bilanga.knowledge.repository.DiseaseKnowledgeRepository;
import com.sni.bilanga.knowledge.repository.DiseaseRiskConditionRepository;
import com.sni.bilanga.knowledge.service.interfaces.DiseaseKnowledgeService;
import com.sni.bilanga.knowledge.service.support.KnowledgeValidator;
import com.sni.bilanga.knowledge.dto.response.*;
import com.sni.bilanga.knowledge.service.support.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiseaseKnowledgeServiceImpl implements DiseaseKnowledgeService {

    private final DiseaseKnowledgeRepository diseaseRepository;
    private final DiseaseRiskConditionRepository conditionRepository;
    private final KnowledgeValidator validator;
    private final KnowledgeMapper mapper;

    // ============================================================
    // Connaissances maladie
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public DiseaseKnowledgeResponse create(DiseaseKnowledgeRequest request) {
        String cropName = validator.requireCrop(request.getCropName(), false);
        String code = requireCode(request.getDiseaseCode());

        if (diseaseRepository.findByCropNameAndDiseaseCode(cropName, code).isPresent()) {
            throw new BusinessRuleException(
                    "Cette maladie est déjà décrite pour la culture " + cropName + ".");
        }

        return mapper.toResponse(diseaseRepository.save(apply(new DiseaseKnowledge(), request, cropName, code)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public DiseaseKnowledgeResponse update(Long id, DiseaseKnowledgeRequest request) {
        DiseaseKnowledge existing = requireDisease(id);
        String cropName = validator.requireCrop(request.getCropName(), false);
        String code = requireCode(request.getDiseaseCode());
        return mapper.toResponse(diseaseRepository.save(apply(existing, request, cropName, code)));
    }

    @Override
    @Transactional(readOnly = true)
    public DiseaseKnowledgeResponse findById(Long id) {
        return mapper.toResponse(requireDisease(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiseaseKnowledgeResponse> findAll(String cropName) {
        List<DiseaseKnowledge> all = diseaseRepository.findAll();
        if (cropName == null || cropName.isBlank()) {
            return all.stream().map(mapper::toResponse).toList();
        }

        String crop = validator.requireCrop(cropName, false);
        return all.stream()
                .filter(d -> crop.equals(d.getCropName()))
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void delete(Long id) {
        DiseaseKnowledge disease = requireDisease(id);

        // Les conditions d'apparition n'ont plus d'objet sans la maladie qu'elles décrivent.
        conditionRepository.deleteAll(
                conditionRepository.findByCropNameAndDiseaseCode(
                        disease.getCropName(), disease.getDiseaseCode()));

        diseaseRepository.delete(disease);
    }

    private DiseaseKnowledge apply(DiseaseKnowledge target, DiseaseKnowledgeRequest r,
                                   String cropName, String code) {
        target.setCropName(cropName);
        target.setDiseaseCode(code);
        target.setDisplayName(r.getDisplayName());
        target.setSymptoms(r.getSymptoms());
        target.setFavorableConditions(r.getFavorableConditions());
        target.setTreatment(r.getTreatment());
        target.setPrevention(r.getPrevention());
        target.setPriority(validator.requirePriority(r.getPriority(), "HAUTE"));
        target.setEstimatedCost(r.getEstimatedCost());
        return target;
    }

    // ============================================================
    // Conditions d'apparition
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public DiseaseRiskConditionResponse createCondition(DiseaseRiskConditionRequest request) {
        return mapper.toResponse(conditionRepository.save(applyCondition(new DiseaseRiskCondition(), request)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public DiseaseRiskConditionResponse updateCondition(Long id, DiseaseRiskConditionRequest request) {
        return mapper.toResponse(conditionRepository.save(applyCondition(requireCondition(id), request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiseaseRiskConditionResponse> findConditions(String cropName) {
        List<DiseaseRiskCondition> conditions = (cropName == null || cropName.isBlank())
                ? conditionRepository.findAll()
                : conditionRepository.findForCrop(validator.requireCrop(cropName, false));
        return conditions.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void deleteCondition(Long id) {
        conditionRepository.delete(requireCondition(id));
    }

    private DiseaseRiskCondition applyCondition(DiseaseRiskCondition target,
                                                DiseaseRiskConditionRequest r) {
        String cropName = validator.requireCrop(r.getCropName(), false);
        String code = requireCode(r.getDiseaseCode());
        String operator = validator.requireOperator(r.getOperator());

        validator.requireCoherentThresholds(operator, r.getThreshold(), r.getThresholdMax());

        // Décrire les conditions d'une maladie inconnue produirait un risque
        // sans traitement associé.
        if (diseaseRepository.findByCropNameAndDiseaseCode(cropName, code).isEmpty()) {
            throw new BusinessRuleException(
                    "Aucune connaissance enregistrée pour " + code + " sur la culture " + cropName
                            + ". Décrivez d'abord la maladie.");
        }

        target.setCropName(cropName);
        target.setDiseaseCode(code);
        target.setMeasureField(validator.requireMeasureField(r.getMeasureField()));
        target.setOperator(operator);
        target.setThreshold(r.getThreshold());
        target.setThresholdMax(r.getThresholdMax());
        target.setWeight(validator.requireWeight(r.getWeight()));
        target.setLabel(r.getLabel());
        target.setActive(r.getActive() == null || r.getActive());
        return target;
    }

    // ============================================================
    // Interne
    // ============================================================
    private String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Le code de la maladie est obligatoire.");
        }
        String value = code.trim();
        if (value.contains("___")) {
            throw new BusinessRuleException(
                    "Le code attendu est normalisé, sans préfixe de culture : "
                            + value.substring(value.lastIndexOf("___") + 3) + " plutôt que " + value + ".");
        }
        return value;
    }

    private DiseaseKnowledge requireDisease(Long id) {
        return diseaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maladie introuvable : " + id));
    }

    private DiseaseRiskCondition requireCondition(Long id) {
        return conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition introuvable : " + id));
    }
}
