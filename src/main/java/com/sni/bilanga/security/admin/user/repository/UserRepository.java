package com.sni.bilanga.security.admin.user.repository;


import com.sni.bilanga.security.admin.user.model.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {

    Optional<Users> findByEmail(String email);

    Optional<Users> findByEmailIgnoreCase(String email);

    Optional<Users> findByUserId(String userId);


    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    boolean existsByEmail(String email);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM users WHERE lower(email) = lower(:email))")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM users WHERE user_id = :userId)")
    boolean existByUserId(String userId);

    @Query(nativeQuery = true, value = "SELECT * FROM users WHERE deleted = false")
    List<Users> findAllActiveUsers();

    @Query(nativeQuery = true, value = "SELECT * FROM users WHERE deleted = true")
    List<Users> findAllDeletedUsers();

    @Query(nativeQuery = true, value = "SELECT * FROM users WHERE deleted = true AND email = :email")
    Optional<Users> findDeletedUserByEmail(@Param("email") String email);

    Page<Users> findAllByDeletedFalse(Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT u.*
            FROM users u
            JOIN search_admin_user(:query) s ON s.id = u.id
            ORDER BY s.score DESC, u.email ASC
            """)
    List<Users> searchAdminUsers(@Param("query") String query);
}
