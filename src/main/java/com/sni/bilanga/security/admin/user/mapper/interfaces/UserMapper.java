package com.sni.bilanga.security.admin.user.mapper.interfaces;




import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.dto.response.UserResponse;
import com.sni.bilanga.security.admin.user.mapper.decorator.UserMapperDecorator;
import com.sni.bilanga.security.admin.user.model.Users;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
@DecoratedWith(UserMapperDecorator.class)
public interface UserMapper {

    //TODO add update method

    @Mapping(target = "generatedPassword", ignore = true)
    @Mapping(target = "businessEntityCode", ignore = true)
    @Mapping(target = "businessEntityName", ignore = true)
    @Mapping(target = "status", ignore = true)
    UserResponse toDTO(Users user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "isAccountExpired", ignore = true)
    @Mapping(target = "isAccountLocked", ignore = true)
    @Mapping(target = "isAccountEnabled", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "roleUsers", ignore = true)
    Users toEntity(UserRequest request);
}
