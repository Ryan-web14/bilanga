package com.sni.bilanga.security.admin.role.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateRolePermissionsRequest(
        @NotEmpty List<Long> permissionIds
) {
}
