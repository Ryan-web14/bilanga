package com.sni.bilanga.security.authentication.handler;


import com.sni.bilanga.audit.service.interfaces.UserSessionService;
import com.sni.bilanga.security.filter.JWTFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomLogoutHandler implements LogoutHandler {
    private final UserSessionService sessionService;
    private final JWTFilter filter;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        sessionService.invalidateSessionByToken(filter.extractToken(request));
    }
}
