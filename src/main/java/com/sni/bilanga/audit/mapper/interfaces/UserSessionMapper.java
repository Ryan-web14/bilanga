package com.sni.bilanga.audit.mapper.interfaces;



import com.sni.bilanga.audit.dto.response.UserSessionResponse;
import com.sni.bilanga.audit.mapper.decorator.UserSessionMapperDecorator;
import com.sni.bilanga.audit.model.UserSession;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
@DecoratedWith(UserSessionMapperDecorator.class)
public interface UserSessionMapper {

    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", ignore = true)
    UserSessionResponse toDTO(UserSession obj);

}
