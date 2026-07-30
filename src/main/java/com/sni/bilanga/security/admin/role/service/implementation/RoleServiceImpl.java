package com.sni.bilanga.security.admin.role.service.implementation;



import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.exception.customs.ValidationException;
import com.sni.bilanga.security.admin.role.dto.request.RoleRequest;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.mapper.interfaces.RoleMapper;
import com.sni.bilanga.security.admin.role.model.Role;
import com.sni.bilanga.security.admin.role.repository.RoleRepository;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleService;
import com.sni.bilanga.utils.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepo;
    private final RoleMapper roleMapper;

    @Override
    public RoleResponse addRole(RoleRequest request){

        log.debug("Adding role with name {}",request.getName());
        List<String> err = validateRole(request);

        if(!err.isEmpty()){
            throw new ValidationException("Invalid role request", err);
        }

        Role role = roleMapper.toEntity(request);

        if(request.getName().equals("ADMIN")){

            role.setIsSystemRole(true);
        }
        role.setVersion(1);
        role = roleRepo.save(role);

        return roleMapper.toDto(role);
    }

    @Override
    public RoleResponse updateRole(String name, RoleRequest request){

        log.debug("updating role with name {}", name);
        List <String> err = validateRole(request);

        try{
            if(!err.isEmpty()){
                log.debug("Invalid role update request ");
                throw new ValidationException("Invalid request", err);
            }

            Role obj = roleRepo.findByName(name)
                    .orElseThrow(()-> new ResourceNotFoundException("Role with name " + name + " not found"));

           obj = roleMapper.mapRoleUpdate(obj, request);

           roleRepo.save(obj);

           return roleMapper.toDto(obj);

        }catch(ValidationException ex){
            log.debug("Error while validating role update request");
            throw new RuntimeException("Invalid request", ex);
        }catch(ResourceNotFoundException ex){
            log.debug("Role with name " + name + " not found");
            throw new BadRequestException("Role with name " + name + " not found");
        }
    }

    @Override
    public void deleteRole(String name){

        Role obj = roleRepo.findByName(name)
                .orElseThrow(()-> new ResourceNotFoundException("Role with name " + name + " not found"));
        if(obj.getIsSystemRole()){
            log.debug("Role with name {} is a system role and cannot be deleted", name);
            throw new BadRequestException("Role with name " + name + " is a system role and cannot be deleted");
        }
        roleRepo.delete(obj);
        log.debug("Role with name {} deleted", name);
    }

    @Override
    public RoleResponse getRole(String name){

        Role obj = roleRepo.findByName(name)
                .orElseThrow(()-> new ResourceNotFoundException("Role with name " + name + " not found"));
        return roleMapper.toDto(obj);
    }

    //Get only active roles
    @Override
    public List<RoleResponse> getAllRoles(){
        return roleRepo.findAllActiveRole().stream()
                .map(roleMapper::toDto)
                .toList();
    }

    @Override
    public List<RoleResponse> getAllRolesAdmin() {
        return roleRepo.findAllByOrderByNameAsc().stream()
                .map(roleMapper::toDto)
                .toList();
    }

    @Override
    public List<RoleResponse> searchByName(String query) {
        if (query == null || query.isBlank()) {
            return getAllRolesAdmin();
        }
        return roleRepo.searchByName(query.trim()).stream()
                .map(roleMapper::toDto)
                .toList();
    }


    public void activateRole(String name){
        Role obj = roleRepo.findByName(name)
                .orElseThrow(()-> new ResourceNotFoundException("Role with name " + name + " not found"));
        obj.setIsActive(true);
        roleRepo.save(obj);
    }

    public void deactivateRole(String name){
        Role obj = roleRepo.findByName(name)
                .orElseThrow(()-> new ResourceNotFoundException("Role with name " + name + " not found"));
        obj.setIsActive(false);
        roleRepo.save(obj);
    }

    public boolean roleExists(String name){
        return roleRepo.existsByName(name);
    }

    @Override
    public Optional<Role> getRoleByIdService(Long id) {
        return Optional.of(roleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role with id " + id + " not found")));
    }

    @Override
    public Optional<Role> getRoleByNameService(String name) {
        return Optional.of(roleRepo.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role with name " + name + " not found")));
    }

    @Override
    public RoleResponse getRoleResponseByEntityService(Role role) {
        return roleMapper.toDto(role);
    }


    private List<String> validateRole(RoleRequest request){

        List<String> err = new ArrayList<>();

        if(request.getName().isEmpty()){
            err.add("Invalid role request, the name is not valid");
        }

        if(StringUtils.hasText(request.getDescription())
                && !ValidationUtils.validateDescription(request.getDescription().trim())){
            err.add("Invalid role request, the description is not valid");
        }

        return err;
    }
}
