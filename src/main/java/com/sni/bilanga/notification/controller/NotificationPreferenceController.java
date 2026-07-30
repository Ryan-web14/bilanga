package com.sni.bilanga.notification.controller;

import com.sni.bilanga.notification.dto.request.NotificationPreferenceRequest;
import com.sni.bilanga.notification.dto.response.NotificationPreferenceResponse;
import com.sni.bilanga.notification.service.NotificationPreferenceService;
import com.sni.bilanga.security.access.AccessGuard;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Réglages de notification de l'utilisateur courant.
 *
 * <p>Aucun identifiant en paramètre : ces réglages sont personnels, et laisser
 * l'appelant désigner l'utilisateur reviendrait à lui permettre de couper les
 * notifications de quelqu'un d'autre. L'identité vient du contexte de sécurité,
 * par {@link AccessGuard#currentUserId()}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final AccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> find() {
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.find(accessGuard.currentUserId())));
    }

    /**
     * Remplacement complet, et non fusion partielle : c'est la seule forme qui
     * laisse l'utilisateur certain de ce qui s'applique après coup.
     */
    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> save(
            @Valid @RequestBody NotificationPreferenceRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Préférences enregistrées.",
                preferenceService.save(accessGuard.currentUserId(), request)));
    }
}
