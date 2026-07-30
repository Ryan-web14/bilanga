package com.sni.bilanga.security.admin.role.service.interfaces;



import com.sni.bilanga.security.admin.role.dto.request.RoleRequest;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleService {

    RoleResponse addRole(RoleRequest request);
    RoleResponse updateRole(String name, RoleRequest request);
    void deleteRole(String name);
    RoleResponse getRole(String name);
    List<RoleResponse> getAllRoles();
    List<RoleResponse> getAllRolesAdmin();
    List<RoleResponse> searchByName(String query);
    void activateRole(  String name);
    void deactivateRole(String name);
    boolean roleExists(String name);
    Optional<Role> getRoleByIdService(Long id);
    Optional<Role> getRoleByNameService(String name);
    RoleResponse getRoleResponseByEntityService(Role role);

}
