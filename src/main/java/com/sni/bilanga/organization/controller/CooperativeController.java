package com.sni.bilanga.organization.controller;

import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.audit.context.AuditContext;
import com.sni.bilanga.audit.util.AuditDiffUtil;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.organization.dto.request.CooperativeRequest;
import com.sni.bilanga.organization.dto.response.CooperativeResponse;
import com.sni.bilanga.organization.service.interfaces.OrganizationService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administration des coopératives.
 *
 * <p><strong>Aucune coopérative n'est requise pour utiliser le système.</strong>
 * Ces routes servent à ceux qui en ont l'usage ; une exploitation indépendante
 * n'en déclare jamais.
 *
 * <p>Même posture que les autres contrôleurs d'administration : {@code @Audited}
 * et {@code @Idempotent} sur les écritures. La garde par permission suit celle
 * des rôles et permissions, ces objets relevant de la structure et non du métier
 * agricole.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/cooperatives")
public class CooperativeController {

    private final OrganizationService organizationService;

    @PostMapping
    @Audited(module = "ORGANIZATION", action = "CREATE", ressource = "cooperative")
    @Idempotent(operation = "COOPERATIVE_CREATE")
    public ResponseEntity<ApiResponse<CooperativeResponse>> create(
            @Valid @RequestBody CooperativeRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coopérative créée.",
                        organizationService.createCooperative(request)));
    }

    /**
     * Modification d'une coopérative, avec l'écart consigné dans
     * {@code diff_json} — voir {@code AdminUserController.update} pour le raisonnement.
     *
     * <p>Instrumentée ici plutôt qu'ailleurs parce qu'une coopérative regroupe
     * plusieurs exploitations : un changement de nom ou de statut se répercute sur
     * l'affichage de toutes leurs parcelles, et « qui a renommé quoi, et quand »
     * est exactement ce qu'on vient chercher dans un journal.
     */
    @PutMapping("/{id}")
    @Audited(module = "ORGANIZATION", action = "UPDATE", ressource = "cooperative")
    @Idempotent(operation = "COOPERATIVE_UPDATE")
    public ResponseEntity<ApiResponse<CooperativeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CooperativeRequest request) {

        CooperativeResponse before = organizationService.findCooperative(id);
        CooperativeResponse after = organizationService.updateCooperative(id, request);

        AuditContext.putMeta("cooperativeId", String.valueOf(id));
        AuditContext.setDiff(AuditDiffUtil.diff(before, after));

        return ResponseEntity.ok(ApiResponse.success("Coopérative mise à jour.", after));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CooperativeResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.findCooperative(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CooperativeResponse>>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                organizationService.searchCooperatives(status, q, pageable))));
    }

    /**
     * Archivage, jamais suppression : les exploitations rattachées survivent.
     * Supprimer ferait disparaître le rattachement historique de dizaines
     * d'exploitations pour une décision administrative.
     */
    @DeleteMapping("/{id}")
    @Audited(module = "ORGANIZATION", action = "ARCHIVE", ressource = "cooperative")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable Long id) {
        organizationService.archiveCooperative(id);
        return ResponseEntity.ok(ApiResponse.success("Coopérative archivée.", null));
    }
}
