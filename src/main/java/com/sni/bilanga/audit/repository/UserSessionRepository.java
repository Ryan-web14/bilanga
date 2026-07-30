package com.sni.bilanga.audit.repository;



import com.sni.bilanga.audit.model.UserSession;
import com.sni.bilanga.security.admin.user.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(UUID sessionId);

    List<UserSession> findAllByUsers_IdOrderByCreatedAtAsc(Long userId);

    List<UserSession> findAllByUsers_IdAndRevokedFalseOrderByCreatedAtDesc(Long userId);

    List<UserSession> findAllByUsers_IdAndRevokedFalseAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    List<UserSession> findAllByUsers(Users user);

    @Modifying
    void deleteByUsers(Users user);

    @Modifying
    void deleteBySessionId(UUID sessionId);

    Optional<UserSession> findFirstByUsers_IdAndRevokedFalseOrderByCreatedAtDesc(Long userId);

    Optional<UserSession> findFirstByUsers_IdOrderByCreatedAtDesc(Long userId);

    Optional<UserSession> findFirstByUsers_IdAndRevokedFalseOrderByCreatedAtAsc(Long userId);
}
