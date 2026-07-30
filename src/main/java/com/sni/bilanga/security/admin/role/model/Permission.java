package com.sni.bilanga.security.admin.role.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "Permission name is required")
    @Size(max = 100, message = "Permission name must not exceed 100 characters")
    @Pattern(regexp = "^[A-Z_]+$", message = "Permission name must contain only uppercase letters and underscores")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    @Column(name = "display_name")
    private String displayName;

    @NotBlank(message = "Module is required")
    @Size(max = 50, message = "Module must not exceed 50 characters")
    @Column(name = "module", nullable = false)
    private String module;

    @NotBlank(message = "Action is required")
    @Size(max = 60, message = "Action must not exceed 60 characters")
    @Column(name = "action", nullable = false)
    private String action;

    // Permission configuration
    @Column(name = "is_system_permission", nullable = false)
    @Builder.Default
    private Boolean isSystemPermission = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public String getFullPermissionName() {
        return module + ":" + action;
    }

}
