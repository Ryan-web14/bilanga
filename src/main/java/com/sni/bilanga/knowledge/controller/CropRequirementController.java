package com.sni.bilanga.knowledge.controller;


import com.sni.bilanga.knowledge.dto.request.CropRequirementRequest;
import com.sni.bilanga.knowledge.dto.request.CropStageRequirementRequest;
import com.sni.bilanga.knowledge.dto.response.CropRequirementResponse;
import com.sni.bilanga.knowledge.dto.response.CropStageRequirementResponse;
import com.sni.bilanga.knowledge.service.interfaces.CropRequirementService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seuils agronomiques de référence.
 *
 * Les listes ne sont volontairement pas paginées : ce sont des tables de
 * configuration bornées — une ligne par culture, une poignée par stade. Les
 * paginer compliquerait la console d'administration sans rien protéger.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/knowledge/crop-requirements")
public class CropRequirementController {

    private final CropRequirementService service;

    @PostMapping
    public ResponseEntity<ApiResponse<CropRequirementResponse>> create(
            @Valid @RequestBody CropRequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seuils enregistrés.", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CropRequirementResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CropRequirementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Seuils mis à jour.", service.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CropRequirementResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CropRequirementResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(service.findAll()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Seuils supprimés.", null));
    }

    // ---------- Infléchissements par stade ----------
    @PostMapping("/stages")
    public ResponseEntity<ApiResponse<CropStageRequirementResponse>> createStage(
            @Valid @RequestBody CropStageRequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seuils de stade enregistrés.", service.createStage(request)));
    }

    @PutMapping("/stages/{id}")
    public ResponseEntity<ApiResponse<CropStageRequirementResponse>> updateStage(
            @PathVariable Long id, @Valid @RequestBody CropStageRequirementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Seuils de stade mis à jour.",
                service.updateStage(id, request)));
    }

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<CropStageRequirementResponse>>> findStages(
            @RequestParam(required = false) String cropName) {
        return ResponseEntity.ok(ApiResponse.success(service.findStages(cropName)));
    }

    @DeleteMapping("/stages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStage(@PathVariable Long id) {
        service.deleteStage(id);
        return ResponseEntity.ok(ApiResponse.success("Seuils de stade supprimés.", null));
    }
}
