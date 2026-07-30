package com.sni.bilanga.audit.mapper.decorator;



import com.sni.bilanga.audit.dto.response.UserSessionResponse;
import com.sni.bilanga.audit.mapper.interfaces.UserSessionMapper;
import com.sni.bilanga.audit.model.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public abstract class UserSessionMapperDecorator implements UserSessionMapper {

    @Autowired
    @Qualifier("delegate")
    private UserSessionMapper delegate;

    @Override
    public UserSessionResponse toDTO(UserSession obj){

        if(obj == null) {
            log.debug("UserSession is null");
            return null;}

        UserSessionResponse response = delegate.toDTO(obj);

        response.setSessionId(obj.getSessionId().toString());
        response.setEmail(obj.getUsers().getEmail());
        response.setRole(obj.getRoleSnapshot());

        if(!obj.isActive()){
            response.setStatus("Expired");
        }else{
            response.setStatus("Active");
        }

        return response;
    }


}
