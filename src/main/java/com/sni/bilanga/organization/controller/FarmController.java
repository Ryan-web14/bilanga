package com.sni.bilanga.organization.controller;

import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.organization.dto.request.FarmMembershipRequest;
import com.sni.bilanga.organization.dto.request.FarmRequest;
import com.sni.bilanga.organization.dto.response.FarmMembershipResponse;
import com.sni.bilanga.organization.dto.response.FarmResponse;
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

import java.util.List;

/**
 * Administration des exploitations et de leurs membres.
 *
 * <p><strong>Aucune exploitation n'est requise pour utiliser le système.</strong>
 * Une parcelle sans exploitation se comporte exactement comme avant
 * l'introduction de ce niveau ; ces routes servent à ceux qui en ont l'usage.
 *
 * <p>L'appartenance <em>ajoute</em> un accès et n'en retire jamais : le
 * propriétaire d'une parcelle garde le sien même s'il n'est pas membre de
 * l'exploitation à laquelle elle est rattachée.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/farms")
public class FarmController {

    private final OrganizationService organizationService;

    @PostMapping
    @Audited(module = "ORGANIZATION", action = "CREATE", ressource = "farm")
    @Idempotent(operation = "FARM_CREATE")
    public ResponseEntity<ApiResponse<FarmResponse>> create(@Valid @RequestBody FarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Exploitation créée.",
                        organizationService.createFarm(request)));
    }

    @PutMapping("/{id}")
    @Audited(module = "ORGANIZATION", action = "UPDATE", ressource = "farm")
    @Idempotent(operation = "FARM_UPDATE")
    public ResponseEntity<ApiResponse<FarmResponse>> update(
            @PathVariable Long id, @Valid @RequestBody FarmRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Exploitation mise à jour.",
                organizationService.updateFarm(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FarmResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.findFarm(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<FarmResponse>>> search(
            @RequestParam(required = false) Long cooperativeId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                organizationService.searchFarms(cooperativeId, ownerId, status, q, pageable))));
    }

    /**
     * Archivage, jamais suppression : les parcelles rattachées survivent et
     * redeviennent indépendantes. Leur propriétaire garde son accès — il ne l'a
     * jamais tenu de l'exploitation.
     */
    @DeleteMapping("/{id}")
    @Audited(module = "ORGANIZATION", action = "ARCHIVE", ressource = "farm")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable Long id) {
        organizationService.archiveFarm(id);
        return ResponseEntity.ok(ApiResponse.success("Exploitation archivée.", null));
    }

    // ============================================================
    // Membres
    // ============================================================

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<FarmMembershipResponse>>> members(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.membersOf(id)));
    }

    /**
     * Ajoute un membre, ou change son rôle s'il en est déjà un.
     *
     * <p>Une seule route pour les deux : obliger l'appelant à savoir d'abord si
     * la personne est membre, pour choisir entre créer et modifier, est une
     * distinction sans intérêt de son point de vue.
     *
     * <p>La réponse énumère les domaines ouverts par le rôle — un administrateur
     * qui attribue « conseiller » doit savoir si cela donne accès aux marges.
     */
    @PostMapping("/{id}/members")
    @Audited(module = "ORGANIZATION", action = "ASSIGN", ressource = "farm_membership")
    public ResponseEntity<ApiResponse<FarmMembershipResponse>> addMember(
            @PathVariable Long id, @Valid @RequestBody FarmMembershipRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Membre enregistré.",
                organizationService.addOrUpdateMember(id, request)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Audited(module = "ORGANIZATION", action = "REVOKE", ressource = "farm_membership")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id, @PathVariable Long userId) {

        organizationService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Membre retiré.", null));
    }
}
