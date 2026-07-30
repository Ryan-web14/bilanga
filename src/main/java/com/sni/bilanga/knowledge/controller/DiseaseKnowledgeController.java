package com.sni.bilanga.knowledge.controller;


import com.sni.bilanga.knowledge.dto.request.DiseaseKnowledgeRequest;
import com.sni.bilanga.knowledge.dto.request.DiseaseRiskConditionRequest;
import com.sni.bilanga.knowledge.dto.response.DiseaseKnowledgeResponse;
import com.sni.bilanga.knowledge.dto.response.DiseaseRiskConditionResponse;
import com.sni.bilanga.knowledge.service.interfaces.DiseaseKnowledgeService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/knowledge/diseases")
public class DiseaseKnowledgeController {

    private final DiseaseKnowledgeService service;

    @PostMapping
    public ResponseEntity<ApiResponse<DiseaseKnowledgeResponse>> create(
            @Valid @RequestBody DiseaseKnowledgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Maladie enregistrée.", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DiseaseKnowledgeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DiseaseKnowledgeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Maladie mise à jour.", service.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DiseaseKnowledgeResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DiseaseKnowledgeResponse>>> findAll(
            @RequestParam(required = false) String cropName) {
        return ResponseEntity.ok(ApiResponse.success(service.findAll(cropName)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Maladie supprimée.", null));
    }

    // ---------- Conditions d'apparition (risque environnemental) ----------
    @PostMapping("/conditions")
    public ResponseEntity<ApiResponse<DiseaseRiskConditionResponse>> createCondition(
            @Valid @RequestBody DiseaseRiskConditionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Condition enregistrée.", service.createCondition(request)));
    }

    @PutMapping("/conditions/{id}")
    public ResponseEntity<ApiResponse<DiseaseRiskConditionResponse>> updateCondition(
            @PathVariable Long id, @Valid @RequestBody DiseaseRiskConditionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Condition mise à jour.",
                service.updateCondition(id, request)));
    }

    @GetMapping("/conditions")
    public ResponseEntity<ApiResponse<List<DiseaseRiskConditionResponse>>> findConditions(
            @RequestParam(required = false) String cropName) {
        return ResponseEntity.ok(ApiResponse.success(service.findConditions(cropName)));
    }

    @DeleteMapping("/conditions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCondition(@PathVariable Long id) {
        service.deleteCondition(id);
        return ResponseEntity.ok(ApiResponse.success("Condition supprimée.", null));
    }
}
