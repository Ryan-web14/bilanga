package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
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

        if (request.getPlotId() != null) {
            device.setPlot(plotService.require(request.getPlotId()));
        }
        device.setDeviceName(request.getDeviceName());
        device.setBatteryLevel(request.getBatteryLevel());
        device.setBatteryVoltage(request.getBatteryVoltage());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setInstalledAt(request.getInstalledAt());
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