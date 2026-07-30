package com.sni.bilanga.iot.controller;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.exception.customs.ServiceUnavailableException;
import com.sni.bilanga.exception.customs.UnauthorizedException;
import com.sni.bilanga.iot.dto.request.IngestBatchRequest;
import com.sni.bilanga.iot.dto.request.IngestReadingRequest;
import com.sni.bilanga.iot.dto.response.IngestBatchResult;
import com.sni.bilanga.iot.dto.response.IngestResult;
import com.sni.bilanga.iot.service.interfaces.IngestService;
import com.sni.bilanga.utils.error.ErrorCode;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réception des relevés émis par le matériel de terrain.
 *
 * L'authentification repose sur une clé partagée transmise en en-tête plutôt
 * que sur un jeton JWT : un microcontrôleur ne dispose ni de la mémoire ni de
 * l'horloge nécessaires pour gérer un cycle de vie de jeton, et l'usage veut
 * qu'on distingue l'authentification des objets de celle des utilisateurs.
 *
 * <p>C'est le seul contrôleur dont les réponses de succès ne sont pas
 * enveloppées dans {@code ApiResponse} : le firmware analyse {@link IngestResult}
 * tel quel. Les erreurs, elles, suivent le format commun {@code ApiError}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/ingest")
public class IngestController {

    private static final String DEVICE_KEY_HEADER = "X-Device-Key";

    private final IngestService ingestService;
    private final BilangaProperties.Ingest ingestConfig;


    @PostMapping("/readings")
    public ResponseEntity<IngestResult> ingest(
            @RequestHeader(value = DEVICE_KEY_HEADER, required = false) String deviceKey,
            @Valid @RequestBody IngestReadingRequest request) {

        requireValidKey(deviceKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ingestService.ingest(request));
    }

    /**
     * Rejeu d'un lot de relevés tamponnés hors ligne.
     *
     * Le lot n'est pas atomique : chaque relevé est accepté ou rejeté pour son
     * propre compte, et le compte rendu indique lesquels ont échoué. Un boîtier
     * revenu après une longue coupure ne perd pas toute sa série parce qu'une
     * trame est corrompue.
     */
    @PostMapping("/readings/batch")
    public ResponseEntity<IngestBatchResult> ingestBatch(
            @RequestHeader(value = DEVICE_KEY_HEADER, required = false) String deviceKey,
            @Valid @RequestBody IngestBatchRequest request) {

        requireValidKey(deviceKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ingestService.ingestBatch(request));
    }

    /**
     * Sonde de disponibilité, utile au boîtier avant d'émettre.
     *
     * Renvoie désormais un objet plutôt que la chaîne « UP » : le boîtier peut
     * caler son horloge sur {@code serverTime} — sans quoi il ne peut pas
     * horodater les relevés qu'il tamponne hors ligne — et savoir si la clé est
     * configurée avant de tenter un envoi qui échouerait.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestParam(required = false) String technicalId) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("serverTime", Instant.now().toString());

        // Un boîtier qui se signale sans avoir de mesure à déposer — sondes
        // débranchées, cycle de veille — reste vivant. Le consigner évite de le
        // compter parmi les muets et d'envoyer quelqu'un chercher une panne de
        // communication inexistante.
        if (technicalId != null && !technicalId.isBlank()) {
            body.put("deviceKnown", ingestService.touchByTechnicalId(technicalId.trim()));
        }

        boolean keyConfigured = ingestConfig.getDeviceKey() != null && !ingestConfig.getDeviceKey().isBlank();
        body.put("ingestReady", !ingestConfig.isRequireDeviceKey() || keyConfigured);

        // Le boîtier sait ainsi s'il doit joindre l'en-tête, sans avoir à
        // essuyer un 401 pour l'apprendre.
        body.put("deviceKeyRequired", ingestConfig.isRequireDeviceKey());
        return ResponseEntity.ok(body);
    }

    private void requireValidKey(String provided) {
        // Interrupteur d'intégration : l'authentification des boîtiers est
        // désactivée, n'importe quel appelant peut déposer un relevé.
        // Voir bilanga.ingest.require-device-key.
        if (!ingestConfig.isRequireDeviceKey()) {
            return;
        }
        if (ingestConfig.getDeviceKey() == null || ingestConfig.getDeviceKey().isBlank()) {
            throw new ServiceUnavailableException(
                    "Aucune clé de boîtier n'est configurée sur le serveur.",
                    ErrorCode.DEVICE_KEY_NOT_CONFIGURED);
        }
        if (provided == null || !constantTimeEquals(ingestConfig.getDeviceKey(), provided)) {
            throw new UnauthorizedException(
                    "Clé de boîtier invalide.", ErrorCode.INVALID_DEVICE_KEY);
        }
    }

    /**
     * Comparaison à durée constante : une comparaison ordinaire s'interrompt au
     * premier caractère divergent, ce qui laisse mesurer la longueur du préfixe
     * correct et permet de reconstituer la clé caractère par caractère.
     *
     * <p>Le passage par une empreinte de taille fixe est délibéré : comparer
     * directement les chaînes obligeait à sortir dès que les longueurs
     * différaient, ce qui divulguait la longueur de la clé.
     */
    private boolean constantTimeEquals(String expected, String provided) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] a = digest.digest(expected.getBytes(StandardCharsets.UTF_8));
            byte[] b = digest.digest(provided.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette plateforme", e);
        }
    }
}
