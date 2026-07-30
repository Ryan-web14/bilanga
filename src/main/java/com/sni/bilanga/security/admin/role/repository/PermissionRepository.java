package com.sni.bilanga.security.admin.role.repository;

import com.sni.bilanga.security.admin.role.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM permission WHERE lower(name) = lower(:name))")
    boolean existsByName(String name);

    @Query(nativeQuery = true, value = "SELECT * FROM permission WHERE is_active = true")
    Optional<List<Permission>> findAllActivePermission();

    @Query(nativeQuery = true, value = "SELECT * FROM permission WHERE is_system_permission  = true")
    Optional<List<Permission>> findSystemPermissions();

    @Query(nativeQuery = true, value = """
            SELECT p.*
            FROM permission p
            JOIN search_admin_permission(:query) s ON s.id = p.id
            ORDER BY s.score DESC, p.module ASC, p.action ASC
            """)
    List<Permission> searchByName(@Param("query") String query);
}
