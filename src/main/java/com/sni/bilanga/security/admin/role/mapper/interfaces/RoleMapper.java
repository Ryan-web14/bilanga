package com.sni.bilanga.security.admin.role.mapper.interfaces;



import com.sni.bilanga.security.admin.role.dto.request.RoleRequest;
import com.sni.bilanga.security.admin.role.dto.response.RoleResponse;
import com.sni.bilanga.security.admin.role.mapper.decorator.RoleMapperDecorator;
import com.sni.bilanga.security.admin.role.model.Role;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
@DecoratedWith(RoleMapperDecorator.class)
public interface RoleMapper {

    @Mapping(target = "permissions", ignore = true)
    RoleResponse toDto(Role role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isSystemRole", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Role toEntity(RoleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "displayName", source = "request.displayName")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "isActive", source = "request.isActive")
    @Mapping(target = "isSystemRole", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
   Role mapRoleUpdate(Role role, RoleRequest request);

}
