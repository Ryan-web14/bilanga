package com.sni.bilanga.iot.service.implementation;


import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.dto.request.ObservationRequest;
import com.sni.bilanga.iot.dto.response.ObservationResponse;
import com.sni.bilanga.iot.model.Observation;
import com.sni.bilanga.iot.repository.ObservationRepository;
import com.sni.bilanga.iot.service.interfaces.ObservationService;
import com.sni.bilanga.iot.service.support.IotMapper;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.sni.bilanga.utils.format.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements ObservationService {

    private final ObservationRepository observationRepository;
    private final PlotService plotService;
    private final UserRepository userRepository;
    private final IotMapper mapper;

    @Override
    @Transactional
    public ObservationResponse create(ObservationRequest request) {
        Observation observation = Observation.builder()
                .plot(plotService.require(request.getPlotId()))
                .user(resolveUser(request.getUserId()))
                .note(request.getNote())
                .photoUrl(request.getPhotoUrl())
                .build();

        return mapper.toResponse(observationRepository.save(observation));
    }

    @Override
    @Transactional
    public ObservationResponse update(Long id, ObservationRequest request) {
        Observation observation = require(id);

        if (request.getPlotId() != null) {
            observation.setPlot(plotService.require(request.getPlotId()));
        }
        if (request.getUserId() != null) {
            observation.setUser(resolveUser(request.getUserId()));
        }
        observation.setNote(request.getNote());
        observation.setPhotoUrl(request.getPhotoUrl());

        return mapper.toResponse(observationRepository.save(observation));
    }

    @Override
    @Transactional(readOnly = true)
    public ObservationResponse findById(Long id) {
        return mapper.toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ObservationResponse> search(Long plotId, Long userId, Instant from, Instant to,
                                           Pageable pageable) {
        return observationRepository.search(plotId, userId, TimeRange.from(from), TimeRange.to(to), pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        observationRepository.delete(require(id));
    }

    private Observation require(Long id) {
        return observationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Observation introuvable : " + id));
    }

    private Users resolveUser(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
    }
}