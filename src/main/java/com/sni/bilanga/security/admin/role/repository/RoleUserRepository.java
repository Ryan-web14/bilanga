package com.sni.bilanga.security.admin.role.repository;

import com.sni.bilanga.security.admin.role.embedded.RoleUserId;
import com.sni.bilanga.security.admin.role.model.RoleUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface  RoleUserRepository extends JpaRepository<RoleUser, RoleUserId> {


    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM role_user WHERE user_id = :userId AND role_id = :roleId")
    void deleteRoleFromUser(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM role_user WHERE role_id = :roleId")
    void deleteAllUserFromRole(@Param("roleId") Long roleId);

    @Query(nativeQuery = true, value = "SELECT * FROM role_user WHERE user_id = :userId")
    List<RoleUser> findByUserId(@Param("userId") Long userId);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM role_user WHERE user_id = :userId AND role_id = :roleId)")
    boolean existsByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Query(nativeQuery = true, value = """
            SELECT EXISTS(
                SELECT 1
                FROM role_user ru
                JOIN role r ON r.id = ru.role_id
                WHERE r.name = :roleName
            )
            """)
    boolean existsAnyUserAssignedToRole(@Param("roleName") String roleName);

}
