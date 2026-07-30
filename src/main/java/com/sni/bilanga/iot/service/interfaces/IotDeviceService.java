package com.sni.bilanga.iot.service.interfaces;

import com.sni.bilanga.enums.EquipmentStatus;
import com.sni.bilanga.iot.dto.request.IotDeviceRequest;
import com.sni.bilanga.iot.dto.response.IotDeviceResponse;
import com.sni.bilanga.iot.model.IotDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IotDeviceService {

    IotDeviceResponse create(IotDeviceRequest request);

    IotDeviceResponse update(Long id, IotDeviceRequest request);

    IotDeviceResponse findById(Long id);

    IotDeviceResponse findByTechnicalId(String technicalId);

    /**
     * Recherche paginée. {@code maxBatteryLevel} isole le matériel dont la
     * charge tombe sous un seuil — la tournée de remplacement des piles se
     * prépare avec cette liste, pas en parcourant tout le parc.
     */
    Page<IotDeviceResponse> search(Long plotId, EquipmentStatus status, Integer maxBatteryLevel,
                                   String term, Pageable pageable);

    void delete(Long id);

    IotDevice require(Long id);
}
