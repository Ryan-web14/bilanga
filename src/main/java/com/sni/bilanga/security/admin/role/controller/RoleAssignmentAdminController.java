package com.sni.bilanga.security.admin.role.controller;


import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.audit.context.AuditContext;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.security.admin.role.dto.request.AssignRoleToUserRequest;
import com.sni.bilanga.security.admin.role.dto.request.UpdateRolePermissionNamesRequest;
import com.sni.bilanga.security.admin.role.dto.request.UpdateRolePermissionsRequest;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.service.interfaces.RolePermissionService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleUserService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin")
public class RoleAssignmentAdminController {

    private final RoleUserService roleUserService;
    private final RolePermissionService rolePermissionService;
    private final RoleService roleService;

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getUserRoles(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(roleUserService.getRoleUsers(userId)));
    }

    @PostMapping("/users/{userId}/roles")
    @Audited(module = "ROLE_USER", action = "ASSIGN_ROLE", ressource = "role_user")
    @Idempotent(operation = "ROLE_USER_ASSIGN")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<Void>> assignRole(@PathVariable String userId,
                                           @Valid @RequestBody AssignRoleToUserRequest request,
                                           Authentication authentication) {
        roleUserService.addRoleToUser(userId, request.roleName(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @Audited(module = "ROLE_USER", action = "REMOVE_ROLE", ressource = "role_user")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<Void>> removeRole(@PathVariable String userId, @PathVariable Long roleId) {
        roleUserService.deleteRoleFromUser(userId, roleId);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('SYSTEM:PERMISSIONS')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(@PathVariable Long roleId) {
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getRolePermissions(roleId)));
    }

    /**
     * Remplace <strong>l'ensemble</strong> des permissions d'un rôle.
     *
     * <p><strong>L'opération la plus sensible de toute l'administration</strong>, et
     * c'est ce qui justifie de l'instrumenter en priorité : elle décide de ce que
     * pourront faire <em>tous</em> les comptes portant ce rôle. Un
     * {@code PATCH} malencontreux qui omettrait {@code SYSTEM:USERS} de la liste
     * retirerait à tous les administrateurs la capacité de gérer les comptes — y
     * compris celle de réparer l'erreur.
     *
     * <p>Le journal consignait seulement « quelqu'un a remplacé les permissions du
     * rôle 3 ». Ce qu'il faut savoir est : <em>lesquelles ont été retirées</em>.
     * D'où un diff exprimé en {@code added} / {@code removed} plutôt que champ par
     * champ — un remplacement d'ensemble se lit en différence d'ensembles, non en
     * comparaison d'attributs.
     */
    @PatchMapping("/roles/{roleId}/permissions")
    @Audited(module = "ROLE_PERMISSION", action = "REPLACE_PERMISSIONS", ressource = "role_permission")
    @Idempotent(operation = "ROLE_PERMISSION_REPLACE")
    @PreAuthorize("hasAuthority('SYSTEM:PERMISSIONS')")
    public ResponseEntity<ApiResponse<Void>> replaceRolePermissions(@PathVariable Long roleId,
                                                       @Valid @RequestBody UpdateRolePermissionsRequest request) {

        Set<String> before = permissionNamesOf(roleId);

        rolePermissionService.deleteAllPermissionFromRole(roleId);
        rolePermissionService.addPermissionToRole(request.permissionIds(), roleId);

        recordPermissionChange(roleId, before, permissionNamesOf(roleId));

        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    private Set<String> permissionNamesOf(Long roleId) {
        return rolePermissionService.getRolePermissions(roleId).stream()
                .map(PermissionResponse::getName)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * Consigne l'écart sous forme d'ensembles.
     *
     * <p>{@code removed} est délibérément le premier renseigné : c'est ce qui
     * <em>retire</em> un droit qui explique un 403 inattendu trois jours plus tard,
     * et c'est donc la ligne qu'on cherche.
     */
    private void recordPermissionChange(Long roleId, Set<String> before, Set<String> after) {
        Set<String> removed = new java.util.LinkedHashSet<>(before);
        removed.removeAll(after);

        Set<String> added = new java.util.LinkedHashSet<>(after);
        added.removeAll(before);

        AuditContext.putMeta("roleId", String.valueOf(roleId));
        AuditContext.putMeta("permissionCountBefore", before.size());
        AuditContext.putMeta("permissionCountAfter", after.size());

        Map<String, Object> diff = new java.util.LinkedHashMap<>();
        diff.put("removed", List.copyOf(removed));
        diff.put("added", List.copyOf(added));
        AuditContext.setDiff(diff);
    }

    @PatchMapping("/roles/{roleId}/permission-names")
    @Audited(module = "ROLE_PERMISSION", action = "REPLACE_PERMISSIONS_BY_NAME", ressource = "role_permission")
    @Idempotent(operation = "ROLE_PERMISSION_REPLACE_BY_NAME")
    @PreAuthorize("hasAuthority('SYSTEM:PERMISSIONS')")
    public ResponseEntity<ApiResponse<Void>> replaceRolePermissionNames(@PathVariable Long roleId,
                                                           @Valid @RequestBody UpdateRolePermissionNamesRequest request) {
        rolePermissionService.replacePermissionsByName(roleId, request.permissionNames());
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @DeleteMapping("/roles/{roleId}/permissions")
    @Audited(module = "ROLE_PERMISSION", action = "CLEAR_PERMISSIONS", ressource = "role_permission")
    @PreAuthorize("hasAuthority('SYSTEM:PERMISSIONS')")
    public ResponseEntity<ApiResponse<Void>> clearRolePermissions(@PathVariable Long roleId) {
        rolePermissionService.deleteAllPermissionFromRole(roleId);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    private String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "SYSTEM"
                : authentication.getName();
    }
}
