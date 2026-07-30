package com.sni.bilanga.security.admin.role.model;



import com.sni.bilanga.security.admin.role.embedded.RolePermissionId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "role_permission")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId Id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "role_fk"))
    private Role role;

    @MapsId("permissionId")
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "permission_fk"))
    private Permission permission;

    @Column(name = "created_by", nullable = false)
    private String created_by;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
