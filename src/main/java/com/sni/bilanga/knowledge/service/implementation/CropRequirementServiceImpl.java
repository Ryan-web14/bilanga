package com.sni.bilanga.knowledge.service.implementation;


import com.sni.bilanga.config.EvictsKnowledgeCaches;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.knowledge.dto.request.CropRequirementRequest;
import com.sni.bilanga.knowledge.dto.request.CropStageRequirementRequest;
import com.sni.bilanga.knowledge.model.CropRequirement;
import com.sni.bilanga.knowledge.model.CropStageRequirement;
import com.sni.bilanga.knowledge.repository.CropRequirementRepository;
import com.sni.bilanga.knowledge.repository.CropStageRequirementRepository;
import com.sni.bilanga.knowledge.service.interfaces.CropRequirementService;
import com.sni.bilanga.knowledge.service.support.KnowledgeValidator;
import com.sni.bilanga.knowledge.dto.response.*;
import com.sni.bilanga.knowledge.service.support.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CropRequirementServiceImpl implements CropRequirementService {

    private final CropRequirementRepository repository;
    private final CropStageRequirementRepository stageRepository;
    private final KnowledgeValidator validator;
    private final KnowledgeMapper mapper;

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CropRequirementResponse create(CropRequirementRequest request) {
        String cropName = normalize(request.getCropName());

        if (repository.findByCropName(cropName).isPresent()) {
            throw new BusinessRuleException(
                    "Des seuils existent déjà pour la culture " + cropName + ".");
        }

        return mapper.toResponse(repository.save(apply(new CropRequirement(), request, cropName)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CropRequirementResponse update(Long id, CropRequirementRequest request) {
        CropRequirement existing = require(id);
        return mapper.toResponse(repository.save(apply(existing, request, normalize(request.getCropName()))));
    }

    @Override
    @Transactional(readOnly = true)
    public CropRequirementResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropRequirementResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    /**
     * Suppression franche : contrairement aux parcelles, des seuils ne portent
     * aucun historique. En revanche, les supprimer prive de diagnostic toutes
     * les parcelles portant cette culture.
     */
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void delete(Long id) {
        repository.delete(require(id));
    }

    private CropRequirement apply(CropRequirement target, CropRequirementRequest r, String cropName) {
        validator.requireOrderedRange("le pH", r.getPhMin(), r.getPhMax());
        validator.requireOrderedRange("l'humidité du sol", r.getHumSolMin(), r.getHumSolMax());
        validator.requireOrderedRange("la température", r.getTempMin(), r.getTempMax());
        requireTolerance(r.getToleranceSecheresse());

        target.setCropName(cropName);
        target.setPhMin(r.getPhMin());
        target.setPhMax(r.getPhMax());
        target.setHumSolMin(r.getHumSolMin());
        target.setHumSolMax(r.getHumSolMax());
        target.setTempMin(r.getTempMin());
        target.setTempMax(r.getTempMax());
        target.setAzoteMin(r.getAzoteMin());
        target.setPhosphoreMin(r.getPhosphoreMin());
        target.setPotassiumMin(r.getPotassiumMin());
        target.setToleranceSecheresse(r.getToleranceSecheresse() == null ? 0d : r.getToleranceSecheresse());
        return target;
    }

    private void requireTolerance(Double tolerance) {
        if (tolerance != null && (tolerance < 0 || tolerance > 1)) {
            throw new BusinessRuleException(
                    "La tolérance à la sécheresse s'exprime entre 0 et 1.");
        }
    }

    /** La base de connaissance stocke les cultures en minuscules. */
    private String normalize(String cropName) {
        return cropName == null ? null : cropName.trim().toLowerCase(Locale.FRANCE);
    }


    // ============================================================
    // Infléchissements par stade
    // ============================================================
    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CropStageRequirementResponse createStage(CropStageRequirementRequest request) {
        String cropName = validator.requireCrop(request.getCropName(), false);
        String stage = requireStage(request.getGrowthStage());

        if (stageRepository.findByCropNameAndGrowthStage(cropName, stage).isPresent()) {
            throw new BusinessRuleException(
                    "Le stade " + stage + " est déjà décrit pour la culture " + cropName + ".");
        }

        return mapper.toResponse(stageRepository.save(applyStage(new CropStageRequirement(), request, cropName, stage)));
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public CropStageRequirementResponse updateStage(Long id, CropStageRequirementRequest request) {
        CropStageRequirement existing = requireStageEntity(id);
        String cropName = validator.requireCrop(request.getCropName(), false);
        return mapper.toResponse(stageRepository.save(
                applyStage(existing, request, cropName, requireStage(request.getGrowthStage()))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropStageRequirementResponse> findStages(String cropName) {
        List<CropStageRequirement> stages = (cropName == null || cropName.isBlank())
                ? stageRepository.findAll()
                : stageRepository.findByCropNameOrderByGrowthStage(validator.requireCrop(cropName, false));
        return stages.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    @EvictsKnowledgeCaches
    public void deleteStage(Long id) {
        stageRepository.delete(requireStageEntity(id));
    }

    private CropStageRequirement applyStage(CropStageRequirement target,
                                            CropStageRequirementRequest r,
                                            String cropName, String stage) {
        validator.requireOrderedRange("le pH", r.getPhMin(), r.getPhMax());
        validator.requireOrderedRange("l'humidité du sol", r.getHumSolMin(), r.getHumSolMax());
        validator.requireOrderedRange("la température", r.getTempMin(), r.getTempMax());
        requireTolerance(r.getToleranceSecheresse());

        target.setCropName(cropName);
        target.setGrowthStage(stage);
        target.setLabel(r.getLabel());
        target.setPhMin(r.getPhMin());
        target.setPhMax(r.getPhMax());
        target.setHumSolMin(r.getHumSolMin());
        target.setHumSolMax(r.getHumSolMax());
        target.setTempMin(r.getTempMin());
        target.setTempMax(r.getTempMax());
        target.setAzoteMin(r.getAzoteMin());
        target.setPhosphoreMin(r.getPhosphoreMin());
        target.setPotassiumMin(r.getPotassiumMin());
        target.setToleranceSecheresse(r.getToleranceSecheresse());
        return target;
    }

    private String requireStage(String growthStage) {
        if (growthStage == null || growthStage.isBlank()) {
            throw new BusinessRuleException("Le stade de croissance est obligatoire.");
        }
        return growthStage.trim().toUpperCase(Locale.FRANCE);
    }

    private CropStageRequirement requireStageEntity(Long id) {
        return stageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stade introuvable : " + id));
    }

    private CropRequirement require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seuils agronomiques introuvables : " + id));
    }
}
