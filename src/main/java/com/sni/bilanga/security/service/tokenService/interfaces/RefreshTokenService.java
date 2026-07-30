package com.sni.bilanga.security.service.tokenService.interfaces;


import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.model.RefreshToken;

public interface RefreshTokenService {

    void storeRefreshToken(String token, Users user);
    boolean isRefreshTokenActive(String token);
    RefreshToken getActiveToken(String token);
    void rotateToken(String oldToken, String newToken);
    void revokeToken(String token);
    void revokeAllTokenForUser(Long userId);
}
