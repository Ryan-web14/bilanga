package com.sni.bilanga.security.admin.role.repository;

import com.sni.bilanga.security.admin.role.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(@Param("name") String name);

    @Query(nativeQuery = true, value = "SELECT EXISTS(SELECT 1 FROM role WHERE lower(name) = lower(:name))")
    boolean existsByName(@Param("name") String name);

    @Query(nativeQuery = true, value = "SELECT * FROM role WHERE is_active = true")
    List<Role> findAllActiveRole();

    List<Role> findAllByOrderByNameAsc();

    @Query(nativeQuery = true, value = """
            SELECT r.*
            FROM role r
            JOIN search_admin_role(:query) s ON s.id = r.id
            ORDER BY s.score DESC, r.name ASC
            """)
    List<Role> searchByName(@Param("query") String query);
}
