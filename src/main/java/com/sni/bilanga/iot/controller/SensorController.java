package com.sni.bilanga.iot.controller;



import com.sni.bilanga.enums.EquipmentStatus;
import com.sni.bilanga.iot.dto.request.SensorRequest;
import com.sni.bilanga.iot.dto.response.SensorResponse;
import com.sni.bilanga.iot.service.interfaces.SensorService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/sensors")
public class SensorController {

    private final SensorService sensorService;

    @PostMapping
    public ResponseEntity<ApiResponse<SensorResponse>> create(@Valid @RequestBody SensorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Capteur enregistré.", sensorService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SensorResponse>> update(@PathVariable Long id,
                                                              @Valid @RequestBody SensorRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Capteur mis à jour.", sensorService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SensorResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sensorService.findById(id)));
    }

    /** {@code deviceId} n'est plus obligatoire : on peut lister par parcelle ou tout le parc. */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<SensorResponse>>> search(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) String sensorType,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                sensorService.search(deviceId, plotId, status, sensorType, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        sensorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Capteur retiré.", null));
    }
}
