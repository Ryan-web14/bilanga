package com.sni.bilanga.security.admin.role.service.implementation;



import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.exception.customs.ValidationException;
import com.sni.bilanga.security.admin.role.dto.request.PermissionRequest;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.mapper.interfaces.PermissionMapper;
import com.sni.bilanga.security.admin.role.model.Permission;
import com.sni.bilanga.security.admin.role.repository.PermissionRepository;
import com.sni.bilanga.security.admin.role.service.interfaces.PermissionService;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Transactional
@Service
@Slf4j
public class  PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final PermissionRepository permissionRepo;
    private final Pattern permissionNamePattern = Pattern.compile("^[a-zA-Z_]+$");

    @Override
    public PermissionResponse createPermission(PermissionRequest request) {

        log.debug("Creating permission with name {}", request.getName());
        var valid = isPermissionValid(request);
        Permission obj = null;

        try {
            if (valid.containsKey(false)) {
                log.debug("Invalid permission request");
                throw new ValidationException("Invalid permission request", valid.get(false));
            } else if (valid.containsKey(true)) {

                obj = permissionMapper.toEntity(request);
                obj = permissionRepo.save(obj);

            }
        } catch (ValidationException ex) {
            throw new RuntimeException("Invalid request", ex);
        }
        return permissionMapper.toDto(obj);
    }

    @Override
    public PermissionResponse updatePermissionByName(String name, PermissionRequest request) {

        log.debug("Updating permission with name {}", name);
        var valid = isPermissionValid(request);
        Permission obj = null;

        try{
            if(valid.containsKey(false)){
                log.debug("Invalid permission update request ");
                throw new ValidationException("Invalid request", valid.get(false));
            }else if(valid.containsKey(true)){
                obj = permissionMapper.updatePermissionFromRequest(
                        permissionRepo.findByName(name)
                                .orElseThrow(
                                        ()->new ResourceNotFoundException("Permission with name "
                                                + name + " not found")), request);
                permissionRepo.save(obj);
            }
        }catch(ValidationException ex){
            throw new RuntimeException("Invalid request", ex);
        }catch(ResourceNotFoundException ex){
            throw new BadRequestException("Permission with name " + name + " not found", ex);
        }

        return permissionMapper.toDto(obj);
    }

    @Override
    public void deletePermissionByName(String name) {

        permissionRepo.delete(permissionRepo.findByName(name)
                .orElseThrow(()->new ResourceNotFoundException("Permission with name " + name + " not found")));

    }

    @Override
    public void activatePermissionByName(String name) {

        Permission obj = permissionRepo.findByName(name)
                .orElseThrow(()->new ResourceNotFoundException("Permission with name " + name + " not found"));
        obj.setIsActive(true);
        permissionRepo.save(obj);
    }

    @Override
    public void deactivatePermissionByName(String name){

        Permission obj = permissionRepo.findByName(name)
                .orElseThrow(()->new ResourceNotFoundException("Permission with name " + name + " not found"));
        obj.setIsActive(false);
        permissionRepo.save(obj);
    }

    @Override
    public List<PermissionResponse> getAllPermission() {

        List<Permission> permissions = permissionRepo.findAll();

        return permissions.stream()
                .map(permissionMapper::toDto)
                .toList();
    }

    @Override
    public List<PermissionResponse> getAllActivePermissions(){

        List<Permission> activePermissions = permissionRepo.findAllActivePermission()
                .orElseThrow(()->new ResourceNotFoundException("No active permissions found"));

        return activePermissions.stream()
                .map(permissionMapper::toDto)
                .toList();
    }

    @Override
    public List<PermissionResponse> getSystemPermissions(){

        List<Permission> systemPermissions = permissionRepo.findSystemPermissions()
                .orElseThrow(()->new ResourceNotFoundException("No system permissions found"));

        return systemPermissions.stream()
                .map(permissionMapper::toDto)
                .toList();
    }

    @Override
    public List<PermissionResponse> searchByName(String query) {
        if (query == null || query.isBlank()) {
            return getAllActivePermissions();
        }
        return permissionRepo.searchByName(query.trim()).stream()
                .map(permissionMapper::toDto)
                .toList();
    }

    @Override
    public PaginatedResponse<PermissionResponse> getPaginatedPermissions(Pageable pageable){

        Page<PermissionResponse> permissions  =  permissionRepo.findAll(pageable).map(permissionMapper::toDto);


        return new PaginatedResponse<>(permissions);
    }


    @Override
    public PermissionResponse getPermissionByName(String name){
        checkPermissionName(name);
        Permission obj = permissionRepo.findByName(name)
                .orElseThrow(()->new ResourceNotFoundException("Permission with name " + name + " not found"));

        return permissionMapper.toDto(obj);

    }

    @Override
    public boolean permissionExists(String name){

        return permissionRepo.existsByName(name);
    }

    private void checkPermissionName(String name){

        if(!permissionNamePattern.matcher(name).matches()){
            throw new BadRequestException("Permission name should contain only letters and underscores");
        }

        if(!permissionRepo.existsByName(name)){
            throw new BadRequestException("Permission with name " + name + " not found");
        }
    }


    @Override
    public Permission getPermissionByNameService(String name){
        checkPermissionName(name);
        return permissionRepo.findByName(name)
                .orElseThrow(()->new ResourceNotFoundException("Permission with name " + name + " not found"));
    }

    @Override
    public Permission getPermissionByIdService(Long id){
        return permissionRepo.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Permission with id " + id + " not found"));
    }

    /**
     * Method to validate permission request return a map with a boolean and a list of errors if th
     * @param request permission request
     * */
    private Map<Boolean, List<String>> isPermissionValid (PermissionRequest request){

        List<String> err = new ArrayList<>();

        if(!permissionNamePattern.matcher(request.getName()).matches()){
            err.add("Permission name should contain only letters and underscores");
        }

        if(permissionRepo.existsByName(request.getName())){
            err.add("Permission with name " + request.getName() + " already exist");
        }

        //TODO: implement verification of action and module

        if(err.isEmpty()){
            return Map.of(true, new ArrayList<>());
        }else{
            return Map.of(false, err);
        }
    }
}
