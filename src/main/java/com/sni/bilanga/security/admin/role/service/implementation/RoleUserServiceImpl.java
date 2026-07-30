package com.sni.bilanga.security.admin.role.service.implementation;



import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.embedded.RoleUserId;
import com.sni.bilanga.security.admin.role.model.Role;
import com.sni.bilanga.security.admin.role.model.RoleUser;
import com.sni.bilanga.security.admin.role.repository.RoleUserRepository;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleUserService;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Transactional
@Service
@Slf4j
public class RoleUserServiceImpl implements RoleUserService {

    private final RoleUserRepository roleUserRepo;
    private final UserService userService;
    private final RoleService roleService;


    @Override
    public void addRoleToUser(Long userId, Long roleId){
        Role role = roleService.getRoleByIdService(roleId).orElseThrow(
                ()-> new ResourceNotFoundException("Role with id " + roleId + " not found")
        );
        addRoleToUser(userId, role.getName(), "System");
    }

    @Override
    public void addRoleToUser(Long userId, String roleName, String assignedBy){
        Users user = userService.getUserByIdForService(userId);
        addRoleToUser(user, roleName, assignedBy);
    }

    @Override
    public void addRoleToUser(String userId, String roleName, String assignedBy) {
        Users user = userService.getUserByUserIdForService(userId);
        addRoleToUser(user, roleName, assignedBy);
    }

    private void addRoleToUser(Users user, String roleName, String assignedBy) {
        Role role = roleService.getRoleByNameService(roleName).orElseThrow(
                ()-> new ResourceNotFoundException("Role with name " + roleName + " not found")
        );

        if(roleUserRepo.existsByUserIdAndRoleId(user.getId(), role.getId())){
            log.debug("user {} already assigned to role {}", user.getEmail(), role.getName());
            return;
        }

        log.debug("add user {} to role {}", user.getEmail(), role.getName());
        RoleUserId id = new RoleUserId(user.getId(), role.getId());

        RoleUser roleUser = RoleUser.builder()
                .id(id)
                .role(role)
                .users(user)
                .assignedAt(LocalDateTime.now())
                .assignedBy(assignedBy)
                .updatedAt(LocalDateTime.now())
                .build();
        roleUserRepo.save(roleUser);
    }

    @Override
    public void deleteRoleFromUser(Long userId, Long roleId){
        log.debug("delete user {} from role {}", userId, roleId);
        roleUserRepo.deleteRoleFromUser(userId, roleId);
    }

    @Override
    public void deleteRoleFromUser(String userId, Long roleId) {
        Users user = userService.getUserByUserIdForService(userId);
        deleteRoleFromUser(user.getId(), roleId);
    }

    @Override
    public void deleteRoleFromAllUser(Long roleId){
        log.debug("delete all user from role {}", roleId);
        roleUserRepo.deleteAllUserFromRole(roleId);
    }

    @Override
    public List<RoleResponse> getRoleUsers(Long id){

        List<RoleUser> roleUsers = roleUserRepo.findByUserId(id);
        List<RoleResponse> rolesResponse = new ArrayList<>();

        for(RoleUser roleUser : roleUsers){
            rolesResponse.add(roleService.getRoleResponseByEntityService(roleUser.getRole()));
        }

        return rolesResponse;
    }

    @Override
    public List<RoleResponse> getRoleUsers(String userId) {
        Users user = userService.getUserByUserIdForService(userId);
        return getRoleUsers(user.getId());
    }

    @Override
    public boolean hasAnyUserAssignedToRole(String roleName) {
        return roleUserRepo.existsAnyUserAssignedToRole(roleName);
    }
}


