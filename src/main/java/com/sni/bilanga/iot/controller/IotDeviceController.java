package com.sni.bilanga.iot.controller;


import com.sni.bilanga.enums.EquipmentStatus;
import com.sni.bilanga.iot.dto.request.IotDeviceRequest;
import com.sni.bilanga.iot.dto.response.IotDeviceResponse;
import com.sni.bilanga.iot.service.interfaces.IotDeviceService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/devices")
public class IotDeviceController {

    private final IotDeviceService iotDeviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<IotDeviceResponse>> create(@Valid @RequestBody IotDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Boîtier enregistré.", iotDeviceService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IotDeviceResponse>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody IotDeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Boîtier mis à jour.", iotDeviceService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IotDeviceResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(iotDeviceService.findById(id)));
    }

    @GetMapping("/technical/{technicalId}")
    public ResponseEntity<ApiResponse<IotDeviceResponse>> findByTechnicalId(@PathVariable String technicalId) {
        return ResponseEntity.ok(ApiResponse.success(iotDeviceService.findByTechnicalId(technicalId)));
    }

    /**
     * {@code maxBatteryLevel=20} donne directement la liste des boîtiers à
     * recharger : c'est la requête qu'on faisait jusqu'ici en rapatriant tout
     * le parc pour le filtrer côté client.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<IotDeviceResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) Integer maxBatteryLevel,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                iotDeviceService.search(plotId, status, maxBatteryLevel, q, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        iotDeviceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Boîtier retiré du parc.", null));
    }
}
