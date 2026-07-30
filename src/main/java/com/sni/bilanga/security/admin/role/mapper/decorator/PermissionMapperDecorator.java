package com.sni.bilanga.security.admin.role.mapper.decorator;




import com.sni.bilanga.security.admin.role.dto.request.PermissionRequest;
import com.sni.bilanga.security.admin.role.dto.response.PermissionResponse;
import com.sni.bilanga.security.admin.role.mapper.interfaces.PermissionMapper;
import com.sni.bilanga.security.admin.role.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;



@Component
@Slf4j
public class PermissionMapperDecorator implements PermissionMapper {

    @Autowired
    @Qualifier("delegate")
    private PermissionMapper delegate;


    @Override
    public Permission toEntity(PermissionRequest permissionRequest) {
        return delegate.toEntity(permissionRequest);
    }

    @Override
    public PermissionResponse toDto(Permission permission) {
        return delegate.toDto(permission);
    }

    @Override
    public Permission updatePermissionFromRequest(Permission obj, PermissionRequest request) {

        obj.setName(obj.getName().equals(request.getName()) ?
                obj.getName() : request.getName().toUpperCase());
        obj.setDisplayName(obj.getDisplayName().equals(request.getName()) ?
                obj.getDisplayName() : request.getDisplayName().toUpperCase());
        obj.setModule(obj.getModule().equals(request.getModule()) ?
                obj.getModule() : request.getModule().toUpperCase());
        obj.setAction(obj.getModule().equals(request.getModule()) ?
                obj.getAction() : request.getAction().toUpperCase());
        obj.setIsActive(request.getIsActive());

        return obj;
    }

}
