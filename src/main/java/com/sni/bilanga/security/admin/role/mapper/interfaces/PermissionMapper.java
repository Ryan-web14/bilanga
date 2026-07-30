package com.sni.bilanga.security.admin.role.mapper.interfaces;


import com.sni.bilanga.security.admin.role.dto.request.PermissionRequest;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.mapper.decorator.PermissionMapperDecorator;
import com.sni.bilanga.security.admin.role.model.Permission;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
@DecoratedWith(PermissionMapperDecorator.class)
public interface PermissionMapper {

    @Mapping(target = "name", source = "obj.name")
    @Mapping(target = "displayName", source = "obj.displayName")
    @Mapping(target = "module", source = "obj.module")
    @Mapping(target = "action", source = "obj.action")
    @Mapping(target = "fullPermissionName", expression = "java(obj.getFullPermissionName())")
    @Mapping(target = "isActive", source = "obj.isActive")
    PermissionResponse toDto(Permission obj);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isSystemPermission", ignore = true)
    Permission toEntity(PermissionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "displayName", source = "request.displayName")
    @Mapping(target = "module", source = "request.module")
    @Mapping(target = "isActive", source = "request.isActive")
    @Mapping(target = "action", source = "request.action")
    @Mapping(target = "isSystemPermission", ignore = true)
    Permission updatePermissionFromRequest(Permission obj, PermissionRequest request);

}
