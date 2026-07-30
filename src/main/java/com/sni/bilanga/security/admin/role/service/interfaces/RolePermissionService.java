package com.sni.bilanga.security.admin.role.service.interfaces;



import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;

import java.util.List;

public interface RolePermissionService {

    void addPermissionToRole(List<Long> permissionIds, Long roleId);
    void replacePermissionsByName(Long roleId, List<String> permissionNames);
    void deletePermissionFromRole(List<Long> permissionIds, Long roleId);
    void deleteAllPermissionFromRole(Long roleId);
    List<PermissionResponse> getRolePermissions(Long id);

}
