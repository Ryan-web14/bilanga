package com.sni.bilanga.security.admin.role.service.implementation;



import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.embedded.RolePermissionId;
import com.sni.bilanga.security.admin.role.mapper.interfaces.PermissionMapper;
import com.sni.bilanga.security.admin.role.model.Permission;
import com.sni.bilanga.security.admin.role.model.Role;
import com.sni.bilanga.security.admin.role.model.RolePermission;
import com.sni.bilanga.security.admin.role.repository.RolePermissionRepository;
import com.sni.bilanga.security.admin.role.service.interfaces.PermissionService;
import com.sni.bilanga.security.admin.role.service.interfaces.RolePermissionService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


@RequiredArgsConstructor
@Transactional
@Service
@Slf4j
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RolePermissionRepository rolePermissionRepo;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final PermissionMapper permissionMapper;

    @Override
    public void addPermissionToRole(List<Long> permissionIds, Long roleId){

        log.debug("find role with id " + roleId);
        Role role = roleService.getRoleByIdService(roleId).orElseThrow(
                ()-> new ResourceNotFoundException("Role with id " + roleId + " not found")
        );

        List<Permission> permissions = new ArrayList<>();

        for(Long permissionId : permissionIds){

            Permission permission = permissionService.getPermissionByIdService(permissionId);
            permissions.add(permission);
        }

        for(Permission permission : permissions){

            log.debug("add permission " + permission.getName() + " to role " + role.getName());
            RolePermission obj = RolePermission.builder()
                    .Id(new RolePermissionId()).build();
            RolePermissionId id = new RolePermissionId(roleId, permission.getId());
            obj.setId(id);
            obj.setPermission(permission);
            obj.setRole(role);
            obj.setCreated_by("SYSTEM");
            rolePermissionRepo.save(obj);
        }

    }

    @Override
    public void replacePermissionsByName(Long roleId, List<String> permissionNames) {
        Role role = roleService.getRoleByIdService(roleId).orElseThrow(
                () -> new ResourceNotFoundException("Role with id " + roleId + " not found")
        );

        deleteAllPermissionFromRole(role.getId());
        List<Long> permissionIds = permissionNames.stream()
                .map(this::normalizePermissionName)
                .distinct()
                .map(permissionService::getPermissionByNameService)
                .map(Permission::getId)
                .toList();
        addPermissionToRole(permissionIds, role.getId());
    }

    @Override
    public void deletePermissionFromRole(List<Long> permissionIds, Long roleId){

        Role role = roleService.getRoleByIdService(roleId).orElseThrow(
                ()-> new ResourceNotFoundException("Role with id " + roleId + " not found")
        );



        for(Long permissionId : permissionIds){
            log.debug("find permission with id {}", permissionId);

            try{
                    Permission permissionObj = permissionService.getPermissionByIdService(permissionId);
                    log.debug("delete permission {} from role {}", permissionObj.getName(), role.getName());
                    rolePermissionRepo.deletePermissionWithRolePermissionId(roleId, permissionId);
            }catch(ResourceNotFoundException ex){
                log.debug("Permission with id {} not found", permissionId);
            }
        }
    }


    @Override
    public void deleteAllPermissionFromRole(Long roleId){
        rolePermissionRepo.deleteByRole(roleId);
    }

    @Override
    public List<PermissionResponse> getRolePermissions(Long id){

        Role role = roleService.getRoleByIdService(id).orElseThrow(
                ()-> new ResourceNotFoundException("Role with id " + id + " not found")
        );

        List<RolePermission> rolePermission = rolePermissionRepo.findByRole(id);

        List<PermissionResponse> permissions = new ArrayList<>();

        for(RolePermission rp : rolePermission){
            permissions.add(permissionMapper.toDto(rp.getPermission()));
        }

        return permissions;
    }

    private String normalizePermissionName(String permissionName) {
        if (permissionName == null) {
            return "";
        }
        return permissionName.trim().replace(':', '_').toUpperCase(Locale.ROOT);
    }
}

