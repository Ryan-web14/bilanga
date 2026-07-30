package com.sni.bilanga.knowledge.service.implementation;


import com.sni.bilanga.config.EvictsKnowledgeCaches;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.knowledge.dto.request.ArbitrationRequest;
import com.sni.bilanga.knowledge.dto.request.CorrelationRuleRequest;
import com.sni.bilanga.knowledge.dto.request.KnowledgeRuleRequest;
import com.sni.bilanga.knowledge.model.CorrelationRule;
import com.sni.bilanga.knowledge.model.KnowledgeRule;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;
import com.sni.bilanga.knowledge.repository.CorrelationRuleRepository;
import com.sni.bilanga.knowledge.repository.KnowledgeRuleRepository;
import com.sni.bilanga.knowledge.repository.RecommendationArbitrationRepository;
import com.sni.bilanga.knowledge.service.interfaces.DecisionRuleService;
import com.sni.bilanga.knowledge.service.support.KnowledgeValidator;
import com.sni.bilanga.knowledge.dto.response.*;
import com.sni.bilanga.knowledge.service.support.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DecisionRuleServiceImpl implements DecisionRuleService {

    private final KnowledgeRuleRepository ruleRepository;
    private final CorrelationRuleRepository correlationRepository;
    private final RecommendationArbitrationRepository arbitrationRepository;
    private final KnowledgeValidator validator;
    private final KnowledgeMapper mapper;

    // ============================================================
    // Règles attachées à un diagnostic capteur
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public KnowledgeRuleResponse createRule(KnowledgeRuleRequest request) {
        return mapper.toResponse(ruleRepository.save(applyRule(new KnowledgeRule(), request)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public KnowledgeRuleResponse updateRule(Long id, KnowledgeRuleRequest request) {
        return mapper.toResponse(ruleRepository.save(applyRule(requireRule(id), request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeRuleResponse> findRules(String category) {
        List<KnowledgeRule> rules = (category == null || category.isBlank())
                ? ruleRepository.findAll()
                : ruleRepository.findByCategory(validator.requireCategory(category));
        return rules.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void deleteRule(Long id) {
        ruleRepository.delete(requireRule(id));
    }

    private KnowledgeRule applyRule(KnowledgeRule target, KnowledgeRuleRequest r) {
        target.setCategory(validator.requireCategory(r.getCategory()));
        target.setCropName(validator.requireCrop(r.getCropName(), true));
        target.setConditionText(r.getConditionText());
        target.setProposedAction(r.getProposedAction());
        target.setEstimatedCost(r.getEstimatedCost());
        target.setPriority(validator.requirePriority(r.getPriority(), "MOYENNE"));
        target.setValidated(r.getValidated() == null || r.getValidated());
        return target;
    }

    // ============================================================
    // Corrélations maladie / mesures
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CorrelationRuleResponse createCorrelation(CorrelationRuleRequest request) {
        return mapper.toResponse(correlationRepository.save(applyCorrelation(new CorrelationRule(), request)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CorrelationRuleResponse updateCorrelation(Long id, CorrelationRuleRequest request) {
        return mapper.toResponse(correlationRepository.save(applyCorrelation(requireCorrelation(id), request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorrelationRuleResponse> findCorrelations() {
        return correlationRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void deleteCorrelation(Long id) {
        correlationRepository.delete(requireCorrelation(id));
    }

    private CorrelationRule applyCorrelation(CorrelationRule target, CorrelationRuleRequest r) {
        String operator = validator.requireOperator(r.getOperator());

        // Le moteur de corrélation ne connaît pas l'intervalle : il compare une
        // mesure à un seuil unique.
        if (KnowledgeValidator.OPERATOR_BETWEEN.equals(operator)) {
            throw new BusinessRuleException(
                    "L'opérateur BETWEEN n'est pas admis pour une corrélation. "
                            + "Employez deux règles, l'une avec >= et l'autre avec <=.");
        }
        validator.requireCoherentThresholds(operator, r.getThreshold(), null);

        target.setCropName(validator.requireCrop(r.getCropName(), true));
        target.setDiseaseCode(blankToAny(r.getDiseaseCode()));
        target.setMeasureField(validator.requireMeasureField(r.getMeasureField()));
        target.setOperator(operator);
        target.setThreshold(r.getThreshold());
        target.setExtraRecommendation(r.getExtraRecommendation());
        target.setPriority(validator.requirePriority(r.getPriority(), "MOYENNE"));
        return target;
    }

    // ============================================================
    // Arbitrages
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public ArbitrationResponse createArbitration(ArbitrationRequest request) {
        return mapper.toResponse(arbitrationRepository.save(applyArbitration(new RecommendationArbitration(), request)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public ArbitrationResponse updateArbitration(Long id, ArbitrationRequest request) {
        return mapper.toResponse(arbitrationRepository.save(applyArbitration(requireArbitration(id), request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArbitrationResponse> findArbitrations() {
        return arbitrationRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void deleteArbitration(Long id) {
        arbitrationRepository.delete(requireArbitration(id));
    }

    private RecommendationArbitration applyArbitration(RecommendationArbitration target,
                                                       ArbitrationRequest r) {
        String a = validator.requireCategory(r.getCategoryA());
        String b = validator.requireCategory(r.getCategoryB());

        if (a.equals(b)) {
            throw new BusinessRuleException(
                    "Un arbitrage concilie deux domaines distincts : les deux catégories sont identiques.");
        }

        target.setCropName(validator.requireCrop(r.getCropName(), true));
        target.setCategoryA(a);
        target.setCategoryB(b);
        target.setSynthesis(r.getSynthesis());
        target.setPriority(validator.requirePriority(r.getPriority(), "HAUTE"));
        target.setActive(r.getActive() == null || r.getActive());
        return target;
    }

    // ============================================================
    // Interne
    // ============================================================
    private String blankToAny(String value) {
        return (value == null || value.isBlank()) ? KnowledgeValidator.ANY : value.trim();
    }

    private KnowledgeRule requireRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Règle introuvable : " + id));
    }

    private CorrelationRule requireCorrelation(Long id) {
        return correlationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Corrélation introuvable : " + id));
    }

    private RecommendationArbitration requireArbitration(Long id) {
        return arbitrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Arbitrage introuvable : " + id));
    }
}
