package com.sni.bilanga.security.repository;

import com.sni.bilanga.security.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long>{

    Optional<PasswordResetToken> findByPasswordToken(String token);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE password_reset_token SET used = true WHERE password_token = :token")
    void invalidateToken(String token);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE password_reset_token SET used = true WHERE user_id = :userId")
    void invalidateAllTokensForUser(Long userId);

    void deleteByPasswordToken(String token);
}
