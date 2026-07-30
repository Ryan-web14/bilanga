package com.sni.bilanga.knowledge.service.interfaces;


import com.sni.bilanga.knowledge.dto.request.CropRequirementRequest;
import com.sni.bilanga.knowledge.dto.request.CropStageRequirementRequest;
import com.sni.bilanga.knowledge.model.CropRequirement;
import com.sni.bilanga.knowledge.model.CropStageRequirement;

import com.sni.bilanga.knowledge.dto.response.*;
import java.util.List;

public interface CropRequirementService {

    CropRequirementResponse create(CropRequirementRequest request);

    CropRequirementResponse update(Long id, CropRequirementRequest request);

    CropRequirementResponse findById(Long id);

    List<CropRequirementResponse> findAll();

    void delete(Long id);

    // --- Infléchissements par stade de croissance ---
    CropStageRequirementResponse createStage(CropStageRequirementRequest request);

    CropStageRequirementResponse updateStage(Long id, CropStageRequirementRequest request);

    List<CropStageRequirementResponse> findStages(String cropName);

    void deleteStage(Long id);
}
