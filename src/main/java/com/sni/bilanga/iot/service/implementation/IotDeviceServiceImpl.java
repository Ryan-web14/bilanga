package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.dto.request.IotDeviceRequest;
import com.sni.bilanga.iot.dto.response.IotDeviceResponse;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.repository.IotDeviceRepository;
import com.sni.bilanga.iot.service.interfaces.IotDeviceService;
import com.sni.bilanga.iot.service.support.IotMapper;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.EquipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IotDeviceServiceImpl implements IotDeviceService {


    private final IotDeviceRepository iotDeviceRepository;
    private final PlotService plotService;
    private final IotMapper mapper;

    /**
     * Sert uniquement à refermer une alerte technique restée sur l'ancienne
     * parcelle quand un boîtier est déplacé — voir {@link #update}.
     */
    private final AlertService alertService;

    @Override
    @Transactional
    public IotDeviceResponse create(IotDeviceRequest request) {
        if (iotDeviceRepository.existsByTechnicalId(request.getTechnicalId())) {
            throw new BusinessRuleException(
                    "Un boîtier porte déjà l'identifiant technique " + request.getTechnicalId());
        }

        IotDevice device = IotDevice.builder()
                .plot(plotService.require(request.getPlotId()))
                .technicalId(request.getTechnicalId())
                .deviceName(request.getDeviceName())
                .status(DomainEnums.nameOf(request.getStatus() == null ? EquipmentStatus.ACTIVE : request.getStatus()))
                .batteryLevel(request.getBatteryLevel())
                .batteryVoltage(request.getBatteryVoltage())
                .firmwareVersion(request.getFirmwareVersion())
                .installedAt(request.getInstalledAt())
                .build();

        return mapper.toResponse(iotDeviceRepository.save(device));
    }

    @Override
    @Transactional
    public IotDeviceResponse update(Long id, IotDeviceRequest request) {
        IotDevice device = require(id);

        // ------------------------------------------------------------
        // Déplacement d'un boîtier d'une parcelle à une autre.
        //
        // Les relevés déjà enregistrés gardent LEUR parcelle : SensorReading la
        // capture à l'ingestion. C'est le bon comportement — une mesure a été
        // prise quelque part, et la réécrire falsifierait l'historique des deux
        // parcelles à la fois.
        // ------------------------------------------------------------
        if (request.getPlotId() != null
                && !request.getPlotId().equals(device.getPlot() == null
                                               ? null : device.getPlot().getId())) {

            Plot previous = device.getPlot();
            device.setPlot(plotService.require(request.getPlotId()));

            // Les alertes techniques sont indexées par (parcelle, signature). Une
            // alerte ouverte sur l'ancienne parcelle n'aurait plus jamais été
            // refermée : la réconciliation cherche sur la parcelle COURANTE du
            // boîtier, qui vient de changer. Elle serait restée ouverte pour
            // toujours, à signaler une sonde qui n'est plus là — et le technicien
            // apprendrait à ignorer une liste qui ne se vide jamais.
            if (previous != null && device.getTechnicalId() != null) {
                alertService.raiseTechnical(previous, device.getTechnicalId(),
                        "Boîtier déplacé vers une autre parcelle.", false);
            }
        }

        // Mise à jour PARTIELLE : un champ absent n'est plus écrasé.
        //
        // Ces cinq champs étaient posés INCONDITIONNELLEMENT. Un client qui
        // n'envoyait que « plotId » — précisément le geste qu'on fait pour
        // déplacer un boîtier — effaçait donc son nom, son niveau de batterie, sa
        // tension, sa version de firmware et sa date d'installation. En silence,
        // avec un 200.
        //
        // C'est le même défaut que celui corrigé sur CropServiceImpl.update(),
        // et il se manifestait ici sur le cas d'usage le plus courant de la route.
        if (request.getDeviceName() != null)     device.setDeviceName(request.getDeviceName());
        if (request.getBatteryLevel() != null)   device.setBatteryLevel(request.getBatteryLevel());
        if (request.getBatteryVoltage() != null) device.setBatteryVoltage(request.getBatteryVoltage());
        if (request.getFirmwareVersion() != null) device.setFirmwareVersion(request.getFirmwareVersion());
        if (request.getInstalledAt() != null)    device.setInstalledAt(request.getInstalledAt());
        // lastSeenAt n'est jamais renseigné par l'API : il constate un fait —
        // le boîtier a parlé — et non une intention d'administration.
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus().name());
        }
        device.setUpdatedAt(Instant.now());

        return mapper.toResponse(iotDeviceRepository.save(device));
    }

    @Override
    @Transactional(readOnly = true)
    public IotDeviceResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public IotDeviceResponse findByTechnicalId(String technicalId) {
        return mapper.toResponse(iotDeviceRepository.findByTechnicalId(technicalId)
                .orElseThrow(() -> new ResourceNotFoundException("Boîtier introuvable : " + technicalId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IotDeviceResponse> search(Long plotId, EquipmentStatus status, Integer maxBatteryLevel,
                                         String term, Pageable pageable) {
        return iotDeviceRepository
                .search(plotId, DomainEnums.nameOf(status), maxBatteryLevel,
                        term == null || term.isBlank()
                                ? null
                                : "%" + term.trim().toLowerCase(java.util.Locale.ROOT) + "%",
                        pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        IotDevice device = require(id);
        device.setStatus(EquipmentStatus.RETIRE.name());
        device.setUpdatedAt(Instant.now());
        iotDeviceRepository.save(device);
    }

    @Override
    @Transactional(readOnly = true)
    public IotDevice require(Long id) {
        return iotDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Boîtier introuvable : " + id));
    }
}