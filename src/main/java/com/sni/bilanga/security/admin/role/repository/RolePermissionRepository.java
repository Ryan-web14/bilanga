package com.sni.bilanga.security.admin.role.repository;


import com.sni.bilanga.security.admin.role.embedded.RolePermissionId;
import com.sni.bilanga.security.admin.role.model.RolePermission;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    @Query(nativeQuery = true, value = "SELECT * FROM role_permission WHERE role_id = :roleId")
    List<RolePermission> findByRole(@Param("roleId") Long roleId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM role_permission WHERE role_id = :roleId AND permission_id = :permissionId")
    void deletePermissionWithRolePermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM role_permission WHERE role_id = :roleId")
    void deleteByRole(@Param("roleId") Long roleId);
}
