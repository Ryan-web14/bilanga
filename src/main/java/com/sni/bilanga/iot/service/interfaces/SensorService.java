package com.sni.bilanga.iot.service.interfaces;


import com.sni.bilanga.enums.EquipmentStatus;
import com.sni.bilanga.iot.dto.request.SensorRequest;
import com.sni.bilanga.iot.dto.response.SensorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SensorService {

    SensorResponse create(SensorRequest request);

    SensorResponse update(Long id, SensorRequest request);

    SensorResponse findById(Long id);

    /** {@code plotId} permet de lister les capteurs d'une parcelle sans passer par ses boîtiers. */
    Page<SensorResponse> search(Long deviceId, Long plotId, EquipmentStatus status,
                                String sensorType, Pageable pageable);

    void delete(Long id);
}
