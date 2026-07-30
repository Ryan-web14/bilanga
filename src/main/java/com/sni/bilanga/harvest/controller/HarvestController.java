package com.sni.bilanga.harvest.controller;

import com.sni.bilanga.harvest.dto.request.HarvestRequest;
import com.sni.bilanga.harvest.dto.response.HarvestResponse;
import com.sni.bilanga.harvest.service.interfaces.HarvestService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/harvests")
public class HarvestController {

    private final HarvestService harvestService;

    @PostMapping
    public ResponseEntity<ApiResponse<HarvestResponse>> create(
            @Valid @RequestBody HarvestRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Récolte enregistrée.", harvestService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HarvestResponse>> update(
            @PathVariable Long id, @Valid @RequestBody HarvestRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Récolte mise à jour.",
                harvestService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HarvestResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(harvestService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<HarvestResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "harvestedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                harvestService.search(plotId, cropId, from, to, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        harvestService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Récolte supprimée.", null));
    }
}
