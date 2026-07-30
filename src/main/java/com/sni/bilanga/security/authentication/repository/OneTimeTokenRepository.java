package com.sni.bilanga.security.authentication.repository;

import com.sni.bilanga.security.authentication.model.OneTimeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    OneTimeToken findByToken(String token);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM one_time_token WHERE token = :token AND expired_at > CURRENT_TIMESTAMP)")
    boolean isValidToken(String token);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM one_time_token WHERE token = :token)")
    boolean existsByToken(String token);

    void deleteByToken(String token);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE one_time_token SET is_used = true WHERE user_id = :userId")
    void invalidateAllTokensForUser(Long userId);
}
