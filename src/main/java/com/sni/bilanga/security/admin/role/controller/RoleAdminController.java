package com.sni.bilanga.security.admin.role.controller;


import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.security.admin.role.dto.request.RoleRequest;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/roles")
public class RoleAdminController {

    private final RoleService roleService;

    @PostMapping
    @Audited(module = "ROLE", action = "CREATE", ressource = "role")
    @Idempotent(operation = "ROLE_CREATE")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(roleService.addRole(request)));
    }

    @PutMapping("/{name}")
    @Audited(module = "ROLE", action = "UPDATE", ressource = "role")
    @Idempotent(operation = "ROLE_UPDATE")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable String name, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.updateRole(name, request)));
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> get(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRole(name)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRolesAdmin()));
    }

    @GetMapping("/search/by-name")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> searchByName(@RequestParam("query") String query) {
        return ResponseEntity.ok(ApiResponse.success(roleService.searchByName(query)));
    }

    @PatchMapping("/{name}/activate")
    @Audited(module = "ROLE", action = "ACTIVATE", ressource = "role")
    @Idempotent(operation = "ROLE_ACTIVATE", requestBodyArgIndex = -1)
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable String name) {
        roleService.activateRole(name);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PatchMapping("/{name}/deactivate")
    @Audited(module = "ROLE", action = "DEACTIVATE", ressource = "role")
    @Idempotent(operation = "ROLE_DEACTIVATE", requestBodyArgIndex = -1)
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable String name) {
        roleService.deactivateRole(name);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @DeleteMapping("/{name}")
    @Audited(module = "ROLE", action = "DELETE", ressource = "role")
    @PreAuthorize("hasAuthority('SYSTEM:ROLES')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String name) {
        roleService.deleteRole(name);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
