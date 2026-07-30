package com.sni.bilanga.security.admin.role.service.interfaces;



import com.sni.bilanga.security.admin.role.dto.request.PermissionRequest;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.model.Permission;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PermissionService {

    PermissionResponse createPermission(PermissionRequest request);
    PermissionResponse updatePermissionByName(String name, PermissionRequest request);
    void deletePermissionByName(String name);
    void activatePermissionByName(String name);
    void deactivatePermissionByName(String name);

    List<PermissionResponse> getAllPermission();
    List<PermissionResponse> getAllActivePermissions();
    List<PermissionResponse> getSystemPermissions();
    List<PermissionResponse> searchByName(String query);

    /**
     * Return a standard response paginated list of permissions
     * @param pageable pageable object
     */
    PaginatedResponse<PermissionResponse> getPaginatedPermissions(Pageable pageable);

    PermissionResponse getPermissionByName(String name);

    /**
     * Get permission by name for service layer
     * @param name permission name
     * */
    Permission getPermissionByNameService(String name);

    /**
     * Get permission by id for service layer
     *
     * @param id permission id
     * */
    Permission getPermissionByIdService(Long id);

    /**
    * Check if permission exists
    * @param name permission name
    * */
    boolean permissionExists(String name);

    //Add hasPermission to verify if role has permission

}
