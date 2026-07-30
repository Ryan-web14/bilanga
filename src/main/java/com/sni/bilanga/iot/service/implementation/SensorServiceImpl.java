package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.iot.dto.request.SensorRequest;
import com.sni.bilanga.iot.dto.response.SensorResponse;
import com.sni.bilanga.iot.model.Sensor;
import com.sni.bilanga.iot.repository.SensorRepository;
import com.sni.bilanga.iot.service.interfaces.IotDeviceService;
import com.sni.bilanga.iot.service.interfaces.SensorService;
import com.sni.bilanga.iot.service.support.IotMapper;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.EquipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorServiceImpl implements SensorService {


    private final SensorRepository sensorRepository;
    private final IotDeviceService iotDeviceService;
    private final IotMapper mapper;

    @Override
    @Transactional
    public SensorResponse create(SensorRequest request) {
        Sensor sensor = Sensor.builder()
                .device(iotDeviceService.require(request.getDeviceId()))
                .sensorType(request.getSensorType())
                .status(DomainEnums.nameOf(request.getStatus() == null ? EquipmentStatus.ACTIVE : request.getStatus()))
                .defaultValue(request.getDefaultValue())
                .build();

        return mapper.toResponse(sensorRepository.save(sensor));
    }

    @Override
    @Transactional
    public SensorResponse update(Long id, SensorRequest request) {
        Sensor sensor = require(id);

        if (request.getDeviceId() != null) {
            sensor.setDevice(iotDeviceService.require(request.getDeviceId()));
        }
        sensor.setSensorType(request.getSensorType());
        sensor.setDefaultValue(request.getDefaultValue());
        if (request.getStatus() != null) {
            sensor.setStatus(request.getStatus().name());
        }

        return mapper.toResponse(sensorRepository.save(sensor));
    }

    @Override
    @Transactional(readOnly = true)
    public SensorResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SensorResponse> search(Long deviceId, Long plotId, EquipmentStatus status,
                                      String sensorType, Pageable pageable) {
        return sensorRepository
                .search(deviceId, plotId, DomainEnums.nameOf(status),
                        sensorType == null || sensorType.isBlank()
                                ? null
                                : sensorType.trim().toUpperCase(java.util.Locale.ROOT),
                        pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Sensor sensor = require(id);
        sensor.setStatus(EquipmentStatus.RETIRE.name());
        sensorRepository.save(sensor);
    }

    private Sensor require(Long id) {
        return sensorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Capteur introuvable : " + id));
    }
}