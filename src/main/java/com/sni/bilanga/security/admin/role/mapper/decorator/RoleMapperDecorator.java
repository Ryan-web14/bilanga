package com.sni.bilanga.security.admin.role.mapper.decorator;



import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.exception.customs.ResourceAlreadyExistException;
import com.sni.bilanga.security.admin.role.dto.request.RoleRequest;
import com.sni.bilanga.security.admin.role.mapper.interfaces.RoleMapper;
import com.sni.bilanga.security.admin.role.model.Role;
import com.sni.bilanga.security.admin.role.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public abstract class RoleMapperDecorator implements RoleMapper {

    @Autowired
    @Qualifier("delegate")
    private RoleMapper delegate;
    private RoleRepository roleRepo;


    @Autowired
    private void setRoleRepository(RoleRepository roleRepository) {
        this.roleRepo = roleRepository;
    }

    @Override
    public Role toEntity(RoleRequest request){

        Role role  = delegate.toEntity(request);

        try{


            if(roleRepo.existsByName(role.getName())){
                throw new ResourceAlreadyExistException("Role with name " + role.getName() + " already exist");
            }

        }catch (ResourceAlreadyExistException e){
            log.debug("error while setting name, role with this name already exist");
            throw new BadRequestException("Role with name " + request.getName() + " already exist");
        } catch (Exception e) {
            log.debug("error while setting business entity for role");
            throw new RuntimeException(e.getMessage());
        }

        return role;
    }

    @Override
    public Role mapRoleUpdate(Role role, RoleRequest request){

        if(!role.getName().equals(request.getName())){
            role.setName(request.getName().toUpperCase());
        }

        if (!role.getDisplayName().equals(request.getDisplayName())){
            role.setDisplayName(request.getDisplayName().toUpperCase());
        }


        if(!role.getDescription().isEmpty()){
            role.setDescription(request.getDescription());
        }

        if(request.getIsActive() == false){
            role.deactivate();
        }

        role.setVersion(role.getVersion() + 1);

        role.setUpdatedAt(LocalDateTime.now());

        return role;
    }

}
