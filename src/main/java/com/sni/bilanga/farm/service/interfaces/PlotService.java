package com.sni.bilanga.farm.service.interfaces;


import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.enums.SoilType;
import com.sni.bilanga.farm.dto.request.PlotRequest;
import com.sni.bilanga.farm.dto.response.PlotResponse;
import com.sni.bilanga.farm.model.Plot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlotService {

    PlotResponse create(PlotRequest request);

    PlotResponse update(Long id, PlotRequest request);

    PlotResponse findById(Long id);

    /**
     * Recherche paginée. Tous les critères sont facultatifs et se combinent.
     * Remplace les anciens {@code findAll()} / {@code findByUser()}, qui
     * ramenaient l'intégralité de la table.
     */
    Page<PlotResponse> search(Long userId, PlotStatus status, SoilType soilType,
                              String term, Pageable pageable);

    void delete(Long id);

    // Usage interne : récupère l'entité ou lève ResourceNotFoundException
    Plot require(Long id);
}
