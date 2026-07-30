package com.sni.bilanga.diagnosis.controller;


import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.dto.response.PointInTimeView;
import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.request.ImageDiagnosisRequest;
import com.sni.bilanga.diagnosis.dto.request.SensorDiagnosisRequest;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisExplanation;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisResult;
import com.sni.bilanga.diagnosis.dto.response.DiagnosticHistoryResponse;
import com.sni.bilanga.diagnosis.service.interfaces.DiagnosisService;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.DiagnosticSource;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.error.ErrorCode;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/diagnosis")
public class DiagnosisController {

    /** Formats que le modèle de vision sait décoder. */
    private static final Set<String> ACCEPTED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private final DiagnosisService diagnosisService;
    private final BilangaProperties.Diagnosis diagnosisConfig;


    // --- Diagnostics fournis ---
    @PostMapping("/image")
    public ResponseEntity<ApiResponse<DiagnosisResult>> fromImage(
            @Valid @RequestBody ImageDiagnosisRequest req) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.processImageDiagnosis(
                req.getPlotId(), req.getCropName(), req.getDiseaseClass(),
                req.getConfidence(), req.getReadingId(), req.getImageUrl())));
    }

    @PostMapping("/sensor")
    public ResponseEntity<ApiResponse<DiagnosisResult>> fromSensor(
            @Valid @RequestBody SensorDiagnosisRequest req) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.processSensorDiagnosis(
                req.getPlotId(), req.getCropName(), req.getCategory(),
                req.getConfidence(), req.getReadingId())));
    }

    // --- Diagnostics par les modèles ---
    // cropName et readingId sont facultatifs : déduits de la parcelle si absents.
    @PostMapping(value = "/image/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DiagnosisResult>> predictFromImage(
            @RequestParam Long plotId,
            @RequestParam(required = false) Culture cropName,
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) Long readingId) {

        requireUsableImage(image);

        return ResponseEntity.ok(ApiResponse.success(diagnosisService.diagnoseFromImage(
                plotId, nameOf(cropName), image, readingId)));
    }

    @PostMapping("/sensor/predict")
    public ResponseEntity<ApiResponse<DiagnosisResult>> predictFromSensor(
            @RequestParam Long plotId,
            @RequestParam(required = false) Culture cropName,
            @RequestParam(required = false) Long readingId) {
        return ResponseEntity.ok(ApiResponse.success(
                diagnosisService.diagnoseFromSensorReading(plotId, nameOf(cropName), readingId)));
    }

    // --- Historique ---
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DiagnosticHistoryResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.findById(id)));
    }

    /**
     * Justification détaillée : pour chaque conseil, la règle d'origine, la
     * mesure concernée, la valeur observée et le seuil franchi.
     *
     * Ces informations étaient écrites en base depuis la migration V9 et
     * n'étaient exposées nulle part. Un conseil qu'on ne peut pas vérifier
     * demande qu'on lui fasse confiance ; celui-ci se laisse contrôler.
     */
    @GetMapping("/{id}/explain")
    public ResponseEntity<ApiResponse<DiagnosisExplanation>> explain(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.explain(id)));
    }

    /**
     * Rejeu : ce que la connaissance <strong>actuelle</strong> conclurait sur le
     * même relevé.
     *
     * <p>La base de connaissance est pilotable par API, mais rien ne disait à
     * l'agronome ce que change un ajustement de seuil : il modifiait, puis attendait
     * le prochain relevé — sur des conditions différentes de celles qui l'avaient
     * interrogé. Le rejeu rend la question vérifiable : « qu'aurait dit le système,
     * sur ce relevé précis, si ce seuil avait été à 32 % ? ».
     *
     * <p><strong>{@code GET} et non {@code POST}</strong> : rien n'est créé, rien
     * n'est modifié. La méthode dit ce que fait la route, et un client peut la
     * rejouer sans conséquence.
     *
     * <p>⚠️ {@code limitation} est toujours renseigné et doit être affiché : un écart
     * constate que la connaissance a changé, non qu'elle a changé en mieux. C'est à
     * l'agronome d'en juger.
     */
    @GetMapping("/{id}/replay")
    public ResponseEntity<ApiResponse<DiagnosisReplay>> replay(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.replay(id)));
    }

    /**
     * Diagnostic à un instant donné : ce que le système voyait, ce qu'il avait
     * conclu, et ce qu'il conclurait aujourd'hui.
     *
     * <p><strong>Le chaînon manquant de l'historique.</strong>
     * {@code GET /plots/{id}/history} rend des intervalles agrégés : un point de courbe
     * porte un {@code bucket}, un décompte et des min/moy/max, mais ni identifiant de
     * relevé ni identifiant de diagnostic. Cliquer sur un creux d'humidité du 12 mars
     * ne menait nulle part — alors que c'est exactement le geste qu'on fait pour
     * comprendre un incident. Le {@code bucket} du point cliqué s'envoie ici tel quel
     * comme valeur de {@code at}.
     *
     * <p>⚠️ <strong>Lisez {@code alignment} avant d'afficher le diagnostic.</strong>
     * {@code SUR_CE_RELEVE} signifie que la conclusion vient de ces mesures ;
     * {@code EN_VIGUEUR} qu'elle est seulement celle qui s'affichait alors, avec son
     * âge dans {@code diagnosticAgeMinutes}. Les confondre attribuerait à une mesure
     * une conclusion qu'elle n'a pas produite — et {@code EN_VIGUEUR} est le cas
     * <em>ordinaire</em>, non l'exception : le système ne conclut pas à chaque relevé.
     *
     * <p>⚠️ {@code limitation} est toujours renseigné. Cette vue superpose trois choses
     * d'inégale solidité — des mesures enregistrées, une conclusion peut-être seulement
     * contemporaine, et un recalcul partiel. Les afficher au même rang ferait passer
     * une reconstitution pour un enregistrement.
     *
     * <p>{@code GET} : rien n'est écrit, la réponse est rejouable et cachable.
     */
    @GetMapping("/at")
    public ResponseEntity<ApiResponse<PointInTimeView>> diagnosisAt(
            @RequestParam Long plotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at,
            @RequestParam(required = false) Culture cropName,
            @RequestParam(required = false) Integer toleranceMinutes) {

        return ResponseEntity.ok(ApiResponse.success(
                diagnosisService.diagnosisAt(plotId, at, nameOf(cropName), toleranceMinutes)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<DiagnosticHistoryResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) DiagnosticSource source,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "diagnosedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                diagnosisService.search(plotId, source, result, minConfidence, from, to, pageable))));
    }

    /**
     * Le fichier était transmis au modèle sans le moindre contrôle : un PDF, un
     * fichier vide ou une photo de 40 Mo partaient au service d'inférence, qui
     * échouait ensuite d'une manière difficile à rattacher à sa cause.
     */
    private void requireUsableImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessRuleException(
                    "Aucune image n'a été transmise.", ErrorCode.INVALID_FILE_FORMAT);
        }
        if (image.getSize() > diagnosisConfig.getImage().getMaxSizeBytes()) {
            throw new BusinessRuleException(
                    String.format(Locale.FRANCE,
                            "L'image dépasse la taille maximale de %.1f Mo.", diagnosisConfig.getImage().getMaxSizeBytes() / 1_048_576d),
                    ErrorCode.FILE_SIZE_EXCEEDED);
        }
        String contentType = image.getContentType();
        if (contentType == null
                || !ACCEPTED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleException(
                    "Format d'image non pris en charge. Formats acceptés : JPEG, PNG, WebP.",
                    ErrorCode.INVALID_FILE_FORMAT);
        }
    }

    /** La base de connaissance stocke les cultures en minuscules. */
    private String nameOf(Culture culture) {
        return culture == null ? null : culture.name().toLowerCase(Locale.ROOT);
    }
}
