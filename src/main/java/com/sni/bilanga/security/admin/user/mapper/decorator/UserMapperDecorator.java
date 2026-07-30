package com.sni.bilanga.security.admin.user.mapper.decorator;




import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.dto.response.UserResponse;
import com.sni.bilanga.security.admin.user.mapper.interfaces.UserMapper;
import com.sni.bilanga.security.admin.user.model.Users;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class UserMapperDecorator implements UserMapper {

    @Autowired
    @Qualifier("delegate")
    private UserMapper delegate;



    @Override
    public UserResponse toDTO(Users user){
        UserResponse response = delegate.toDTO(user);
        response.setUserId(user.getUserId());
        return response;
    }

    public Users toEntity(UserRequest request){
        Users user = delegate.toEntity(request);
        return user;
    }
}
