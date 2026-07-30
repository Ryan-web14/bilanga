package com.sni.bilanga.intervention.service.interfaces;

import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.intervention.dto.request.InterventionRequest;
import com.sni.bilanga.intervention.dto.response.InterventionEffect;
import com.sni.bilanga.intervention.dto.response.InterventionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Journal de ce qui a été fait au champ.
 *
 * <p>C'est le chaînon qui manquait : le système conseillait sans jamais savoir
 * si le conseil avait été suivi, ni ce qu'il avait produit.
 */
public interface InterventionService {

    InterventionResponse create(InterventionRequest request);

    InterventionResponse update(Long id, InterventionRequest request);

    InterventionResponse findById(Long id);

    Page<InterventionResponse> search(Long plotId, Long cropId, InterventionType type,
                                      Instant from, Instant to, Pageable pageable);

    void delete(Long id);

    /**
     * Ce que l'intervention a changé, mesuré sur les fenêtres qui l'encadrent.
     *
     * <p>Le verdict constate une évolution ; il n'établit pas une causalité, et
     * la réponse le dit explicitement. Un chiffre livré sans réserve serait lu
     * comme une démonstration.
     */
    InterventionEffect effect(Long id);
}
