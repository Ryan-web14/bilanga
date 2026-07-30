package com.sni.bilanga.audit.context;

import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditContextProvider {

    public Long userIdOrNull() {
        UserPrincipal principal = principal();
        return principal == null ? null : principal.getUser().getId();
    }

    public String emailOrSystem() {
        UserPrincipal principal = principal();
        return principal == null ? "SYSTEM" : principal.getUser().getEmail();
    }

    public Long userId(){
        var userPrincipal = principal();

        if(userPrincipal == null){
            throw new IllegalStateException("No user found in SecurityContext");
        }

        return userPrincipal.getUser().getId();
    }

    public String email(){
        var userPrincipal = principal();

        if(userPrincipal == null){
            throw new IllegalStateException("No user found in SecurityContext");
        }

        return userPrincipal.getUser().getEmail();
    }

    public UserPrincipal principal(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        var principal = authentication.getPrincipal();

        return (principal instanceof UserPrincipal userPrincipal) ? userPrincipal : null;
    }

}
