package com.sni.bilanga.audit.service.interfaces;



import com.sni.bilanga.audit.dto.response.UserSessionResponse;
import com.sni.bilanga.audit.dto.response.UserSessionToken;
import com.sni.bilanga.audit.model.UserSession;
import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;


public interface UserSessionService {

    /**
     * This method create a new session for the user and return the access token and refresh token
     *
     * @return : Map<String,String>
     * @Param : httpServletRequest
     * @Param : user
     *
     *
     */
    UserSessionToken createSession(HttpServletRequest request, UserPrincipal user);

    void deleteSessionByToken(String token);
    void deleteAllSessions(Users user);
    void deleteOldestSession(Users user);
    void invalidateAllSessions();
    void invalidateSessionByToken(String token);
    void invalidateSessionByUser(Users user);
    void invalidateOldestSessionByUser(Users user);
    List<UserSessionResponse> getAllSessions();
    public List<UserSessionResponse> getAllActiveSessionsForUser(Users user);
    UserSessionResponse getCurrentSession(String token);
    void refreshSession(String sessionId, String token);
    UserSession getSessionByTokenService(String token);
    boolean isSessionValid(String token);
    boolean isSessionRevoked(String sessionId);
    //List<UserSessionResponse> getAllSessionsByUser(Users user);
    //List<UserSessionResponse> getAllSessionsByIp(String ip);

}
