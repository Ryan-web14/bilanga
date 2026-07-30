package com.sni.bilanga.iot.service.interfaces;


import com.sni.bilanga.iot.dto.request.ObservationRequest;
import com.sni.bilanga.iot.dto.response.ObservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface ObservationService {

    ObservationResponse create(ObservationRequest request);

    ObservationResponse update(Long id, ObservationRequest request);

    ObservationResponse findById(Long id);

    Page<ObservationResponse> search(Long plotId, Long userId, Instant from, Instant to,
                                     Pageable pageable);

    void delete(Long id);
}
