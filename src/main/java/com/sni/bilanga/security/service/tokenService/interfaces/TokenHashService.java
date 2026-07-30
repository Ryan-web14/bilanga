package com.sni.bilanga.security.service.tokenService.interfaces;

public interface TokenHashService {

    String hash(String rawToken);
    boolean matches(String rawToken, String storedHash);
}
