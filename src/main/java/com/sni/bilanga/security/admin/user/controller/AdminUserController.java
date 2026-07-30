package com.sni.bilanga.security.admin.user.controller;


import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.audit.context.AuditContext;
import com.sni.bilanga.audit.util.AuditDiffUtil;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.security.admin.user.dto.request.AdminCreateUserRequest;
import com.sni.bilanga.security.admin.user.dto.request.AdminResetUserPasswordRequest;
import com.sni.bilanga.security.admin.user.dto.request.AdminUpdateUserRequest;
import com.sni.bilanga.security.admin.user.dto.response.AdminUserResponse;
import com.sni.bilanga.security.admin.user.service.interfaces.UserAdminService;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @Audited(module = "USER", action = "ADMIN_CREATE", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_CREATE", required = false)
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> create(@Valid @RequestBody AdminCreateUserRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userAdminService.createUser(request, actor(authentication))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminUserResponse>>> list(@RequestParam(required = false) String email,
                                                                     @RequestParam(required = false) Boolean enabled,
                                                                     @RequestParam(required = false) Boolean locked,
                                                                     @RequestParam(required = false) Boolean deleted,
                                                                     Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.list(email, enabled, locked, deleted, pageable)));
    }

    @GetMapping("/search/by-name")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> searchByName(@RequestParam("query") String query) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.searchByName(query)));
    }

    @GetMapping("/{userCode}")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getByCode(@PathVariable String userCode) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.getByCode(userCode)));
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.getByEmail(email)));
    }

    /**
     * Modification d'un compte, <strong>avec l'écart consigné</strong>.
     *
     * <p>{@code AuditContext} était câblé depuis l'origine mais jamais appelé :
     * {@code metadata_json} et {@code diff_json} restaient vides, et le journal
     * disait <em>qui</em> et <em>quoi</em>, jamais <em>quel changement</em>.
     * Savoir qu'un administrateur a modifié le compte {@code USR-00042} ne
     * répond pas à la question qu'on se pose en relisant un journal après un
     * incident : qu'a-t-il changé ?
     *
     * <p><strong>Le coût assumé : une lecture de plus.</strong> Obtenir l'état
     * antérieur demande un {@code getByCode} supplémentaire. C'est acceptable ici
     * — une modification de compte n'est pas un chemin chaud, et l'alternative
     * (instrumenter le service) mêlerait la journalisation à la logique métier.
     *
     * <p>Le diff porte sur le <strong>DTO de réponse</strong>, non sur l'entité :
     * il ne peut donc contenir ni hachage de mot de passe, ni association
     * paresseuse. {@code AuditDiffUtil} masque en plus tout champ dont le nom
     * évoque un secret — double garde, parce qu'une table d'audit se conserve
     * longtemps et se lit largement.
     */
    @Audited(module = "USER", action = "ADMIN_UPDATE", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_UPDATE", required = false)
    @PutMapping("/{userCode}")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> update(@PathVariable String userCode,
                                                    @Valid @RequestBody AdminUpdateUserRequest request,
                                                    Authentication authentication) {

        AdminUserResponse before = userAdminService.getByCode(userCode);
        AdminUserResponse after = userAdminService.update(userCode, request, actor(authentication));

        AuditContext.putMeta("userCode", userCode);
        AuditContext.setDiff(AuditDiffUtil.diff(before, after));

        return ResponseEntity.ok(ApiResponse.success(after));
    }

    @Audited(module = "USER", action = "ADMIN_ACTIVATE", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_ACTIVATE", required = false)
    @PatchMapping({"/{userCode}/activate", "/{userCode}/enable"})
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> activate(@PathVariable String userCode) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.activate(userCode)));
    }

    @Audited(module = "USER", action = "ADMIN_DEACTIVATE", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_DEACTIVATE", required = false)
    @PatchMapping({"/{userCode}/deactivate", "/{userCode}/disable"})
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> deactivate(@PathVariable String userCode) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.deactivate(userCode)));
    }

    @Audited(module = "USER", action = "ADMIN_UNLOCK", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_UNLOCK", required = false)
    @PatchMapping("/{userCode}/unlock")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unlock(@PathVariable String userCode) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.unlock(userCode)));
    }

    @Audited(module = "USER", action = "ADMIN_RESET_PASSWORD", ressource = "user")
    @Idempotent(operation = "ADMIN_USER_RESET_PASSWORD", required = false)
    @PatchMapping("/{userCode}/password/reset")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> resetPassword(@PathVariable String userCode,
                                                           @RequestBody(required = false) AdminResetUserPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.resetPassword(userCode, request)));
    }

    @Audited(module = "USER", action = "ADMIN_INITIATE_PASSWORD_RESET", ressource = "user")
    @PostMapping("/{userCode}/reset-password")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<Void>> initiatePasswordReset(@PathVariable String userCode,
                                                      Authentication authentication) {
        userAdminService.initiatePasswordReset(userCode, actor(authentication));
        return ResponseEntity.accepted().build();
    }

    @Audited(module = "USER", action = "ADMIN_ARCHIVE", ressource = "user")
    @DeleteMapping("/{userCode}")
    @PreAuthorize("hasAuthority('SYSTEM:USERS')")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable String userCode) {
        userAdminService.archive(userCode);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    private String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "SYSTEM"
                : authentication.getName();
    }
}
